package io.quarkiverse.tekton.it;

import static io.quarkiverse.tekton.it.TektonResourceGenerator.*;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.fabric8.tekton.v1.PipelineRun;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class TektonExtensionDevModeIT {

    private static final Logger LOG = Logger.getLogger(TektonExtensionDevModeIT.class);
    private static KubernetesClient client;
    private static String TEKTON_NAMESPACE;
    private static final long TIMEOUT = 180;

    @BeforeAll
    public static void setup() {
        var objectMapper = new ObjectMapper();
        objectMapper.addMixIn(ObjectMeta.class, ObjectMetaMixin.class);

        var kubernetesSerialization = new KubernetesSerialization(objectMapper, true);
        client = new KubernetesClientBuilder()
                .withConfig(Config.fromKubeconfig(
                        ConfigProvider.getConfig().getValue("quarkus.tekton.devservices.kube-config", String.class)))
                .withKubernetesSerialization(kubernetesSerialization)
                .build();

        TEKTON_NAMESPACE = ConfigProvider.getConfig().getValue("quarkus.tekton.devservices.controller-namespace", String.class);
    }

    /*
     * TODO
     */
    @Test
    @Order(1)
    public void testCase() throws NoSuchAlgorithmException, KeyManagementException, JsonProcessingException {
        LOG.info(">>> Running the test case");

        // Deploy the Tasks: hello, goodbye and Pipeline hello-goodbye
        client.resource(populateHelloTask())
                .inNamespace(TEKTON_NAMESPACE)
                .create();

        client.resource(populateGoodbyeTask())
                .inNamespace(TEKTON_NAMESPACE)
                .create();

        client.resource(populateHelloGoodbyePipeline())
                .inNamespace(TEKTON_NAMESPACE)
                .create();

        // Run the Tekton pipeline
        client.resource(populatePipelineRun())
                .inNamespace(TEKTON_NAMESPACE)
                .create();

        LOG.info("Checking when Tekton Pipeline will end");
        try {
            client.resources(PipelineRun.class)
                    .inNamespace(TEKTON_NAMESPACE)
                    .withName("hello-goodbye-run")
                    .waitUntilCondition(a -> a != null &&
                            a.getStatus() != null &&
                            a.getStatus().getConditions() != null &&
                            a.getStatus().getConditions().get(0).getType().equals("Succeeded"), TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.error(client.getKubernetesSerialization()
                    .asYaml(client.genericKubernetesResources("apiVersion", "tekton.dev/v1")
                            .inNamespace(TEKTON_NAMESPACE)
                            .withName("hello-goodbye-run").get()));
        }
        LOG.infof("Tekton PipelineRun status");
        PipelineRun pipelineRun = client.resources(PipelineRun.class)
                .inNamespace(TEKTON_NAMESPACE)
                .withName("hello-goodbye-run").get();
        LOG.warn(client.getKubernetesSerialization().asYaml(pipelineRun));
    }
}
