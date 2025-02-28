package io.quarkiverse.tekton.deployment.devservices;

import static io.quarkiverse.tekton.deployment.devservices.Utils.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.internal.KubeConfigUtils;
import io.quarkiverse.tekton.deployment.TektonProcessor;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.devservices.common.ContainerShutdownCloseable;
import io.quarkus.kubernetes.client.spi.KubernetesDevServiceInfoBuildItem;
import io.quarkus.kubernetes.client.spi.KubernetesDevServiceRequestBuildItem;

@BuildSteps(onlyIfNot = IsNormal.class, onlyIf = { DevServicesConfig.Enabled.class })
public class TektonExtensionProcessor {
    private static final Logger LOG = Logger.getLogger(TektonExtensionProcessor.class);

    private static final String TEKTON_DASHBOARD_NAME = "tekton-dashboard";

    static volatile DevServicesResultBuildItem.RunningDevService devService;

    @BuildStep(onlyIfNot = IsNormal.class, onlyIf = { DevServicesConfig.Enabled.class })
    void requestKube(TektonDevServiceConfig config,
            BuildProducer<KubernetesDevServiceRequestBuildItem> kubeDevServiceRequest) {
        if (config.enabled()) {
            kubeDevServiceRequest.produce(
                    // Specify the type of the kind test container to launch and enable its launch
                    new KubernetesDevServiceRequestBuildItem(config.clusterType()));
        }
    }

    @BuildStep
    public void deployTekton(
            TektonDevServiceConfig tektonConfig,
            Optional<KubernetesDevServiceInfoBuildItem> kubeServiceInfo,
            BuildProducer<DevServicesResultBuildItem> devServicesResultBuildItem,
            BuildProducer<TektonDevServiceInfoBuildItem> tektonDevServiceInfoBuildItemBuildProducer) {

        if (devService != null) {
            // only produce DevServicesResultBuildItem when the dev service first starts.
            throw new RuntimeException("Dev services already started");
        }

        if (!tektonConfig.enabled() && !kubeServiceInfo.isPresent()) {
            // Tekton Dev Service not enabled and Kubernetes test container has not been created ...
            throw new RuntimeException(
                    "Dev services is not enabled for Tekton and Kubernetes test container has not been created ...");
        }

        // Convert the kube config yaml to its Java Class
        Config kubeConfig = KubeConfigUtils.parseConfigFromString(kubeServiceInfo.get().getKubeConfig());

        if (tektonConfig.debugEnabled()) {
            LOG.info(">>> Cluster container name : " + kubeServiceInfo.get().getContainerId());
            kubeConfig.getClusters().stream().forEach(c -> {
                LOG.debugf(">>> Cluster name: %s", c.getName());
                LOG.debugf(">>> API URL: %s", c.getCluster().getServer());
            });
            kubeConfig.getUsers().stream().forEach(u -> LOG.debugf(">>> User key: %s", u.getUser().getClientKeyData()));
            kubeConfig.getContexts().stream().forEach(ctx -> LOG.debugf(">>> Context : %s", ctx.getContext().getUser()));
        }

        // Create the Kubernetes client using the Kube YAML Config
        KubernetesClient client = new KubernetesClientBuilder()
                .withConfig(io.fabric8.kubernetes.client.Config.fromKubeconfig(kubeServiceInfo.get().getKubeConfig()))
                .build();

        // Pass the configuration parameters to the utility class
        setConfig(tektonConfig);
        setKubernetesClient(client);

        // TODO: To be removed when the issue https://github.com/dajudge/kindcontainer/issues/363 is fixed and released in 1.4.9
        // Patch the node created to add the ingress label
        // ingress-ready: "true"
        LOG.info("Patching the node's label to add: ingress-ready: true");
        client.nodes().withName("kind").edit(
                n -> new NodeBuilder(n).editMetadata().addToLabels("ingress-ready", "true").endMetadata().build());

        // Install the ingress controller
        List<HasMetadata> items = client.load(fetchIngressResourcesFromURL(tektonConfig.ingress().version())).items();
        LOG.info("Deploying the ingress controller resources ...");
        for (HasMetadata item : items) {
            var res = client.resource(item).create();
            assertNotNull(res);
        }

        waitTillPodSelectedByLabelsIsReady(
                Map.of(
                        "app.kubernetes.io/name", "ingress-nginx",
                        "app.kubernetes.io/component", "controller"),
                "ingress-nginx");

        var TEKTON_CONTROLLER_NAMESPACE = tektonConfig.controllerNamespace();
        // Install the Tekton resources from the YAML manifest file
        items = client.load(fetchTektonResourcesFromURL(tektonConfig.version())).items();
        LOG.info("Deploying the tekton resources ...");
        for (HasMetadata item : items) {
            var res = client.resource(item).create();
            assertNotNull(res);
        }

        // Waiting till the Tekton pods are ready/running ...
        waitTillPodSelectedByLabelsIsReady(
                Map.of("app.kubernetes.io/name", "controller",
                        "app.kubernetes.io/part-of", "tekton-pipelines"),
                TEKTON_CONTROLLER_NAMESPACE);

        // TODO
        items = client.load(fetchTektonDashboardResourcesFromURL()).items();
        LOG.info("Deploying the tekton dashboard resources ...");
        for (HasMetadata item : items) {
            var res = client.resource(item).inNamespace(TEKTON_CONTROLLER_NAMESPACE);
            res.create();
            assertNotNull(res);
        }

        // Waiting till the Tekton dashboard pod is ready/running ...
        waitTillPodSelectedByLabelsIsReady(
                Map.of("app.kubernetes.io/name", "dashboard",
                        "app.kubernetes.io/part-of", "tekton-dashboard"),
                TEKTON_CONTROLLER_NAMESPACE);

        // Create the Tekton dashboard ingress route
        LOG.info("Creating the ingress route for the tekton dashboard ...");
        Ingress tektonIngressRoute = new IngressBuilder()
        // @formatter:off
                .withNewMetadata()
                  .withName("tekton-ui")
                  .withNamespace(TEKTON_CONTROLLER_NAMESPACE)
                .endMetadata()
                .withNewSpec()
                  .addNewRule()
                    .withHost(tektonConfig.hostName())
                    .withNewHttp()
                      .addNewPath()
                        .withPath("/")
                        .withPathType("Prefix") // This field is mandatory
                        .withNewBackend()
                          .withNewService()
                            .withName(TEKTON_DASHBOARD_NAME)
                            .withNewPort().withNumber(9097).endPort()
                          .endService()
                        .endBackend()
                      .endPath()
                    .endHttp()
                  .endRule()
                .endSpec()
                .build();
                // @formatter:on
        client.resource(tektonIngressRoute).create();

        if (tektonConfig.ingress().portForwardEnabled()) {
            // Port-forward the traffic from host port to pod's container's port
            Pod tektonDashboardPod = client.pods()
                    .inNamespace(TEKTON_CONTROLLER_NAMESPACE)
                    .withLabels(Map.of("app.kubernetes.io/name", "dashboard",
                            "app.kubernetes.io/part-of", TEKTON_DASHBOARD_NAME))
                    .list().getItems().get(0);

            LOG.info("Launch Port Forward ...");
            LocalPortForward portForward = client.pods()
                    .resource(tektonDashboardPod)
                    .portForward(9097, Integer.parseInt(tektonConfig.hostPort()));
            LOG.infof("Pod's container port: %d forwarded to the host port: %d", 9097, portForward.getLocalPort());
        }

        if (tektonConfig.debugEnabled()) {
            // List the pods running under the Tekton controller namespace
            client.resources(Pod.class)
                    .inNamespace(TEKTON_CONTROLLER_NAMESPACE)
                    .list().getItems().stream().forEach(p -> {
                        LOG.infof("Pod : %, status: %s", p.getMetadata().getName(),
                                p.getStatus().getConditions().get(0).getStatus());
                    });
        }

        // TODO: To be reviewed in order to pass tekton parameters for the service consuming the extension
        Map<String, String> configOverrides = Map.of(
                "quarkus.tekton.devservices.controller-namespace", TEKTON_CONTROLLER_NAMESPACE,
                "quarkus.tekton.devservices.kube-config", kubeServiceInfo.get().getKubeConfig());

        tektonDevServiceInfoBuildItemBuildProducer.produce(
                new TektonDevServiceInfoBuildItem(
                        tektonConfig.hostName(),
                        Integer.parseInt(tektonConfig.hostPort())));

        devServicesResultBuildItem.produce(new DevServicesResultBuildItem.RunningDevService(
                TektonProcessor.FEATURE,
                kubeServiceInfo.get().getContainerId(),
                new ContainerShutdownCloseable(new DummyContainer(), TektonProcessor.FEATURE),
                configOverrides).toBuildItem());
    }

    private class DummyContainer extends GenericContainer<DummyContainer> implements Closeable {
        private static final Logger LOG = Logger.getLogger(DummyContainer.class);

        @Override
        public void close() {
            LOG.info("Closing the tekton container ...");
        }
    }
}
