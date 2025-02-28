package io.quarkiverse.tekton.deployment.devservices;

import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;

public class Utils {
    private static final Logger LOG = Logger.getLogger(Utils.class);
    private static TektonDevServiceConfig config;
    private static KubernetesClient client;

    protected static InputStream fetchTektonResourcesFromURL(String version) {
        InputStream resourceAsStream = null;
        try {
            resourceAsStream = new URL(
                    "https://github.com/tektoncd/pipeline/releases/download/" + version + "/release.yaml")
                    .openStream();
        } catch (Exception e) {
            LOG.error("The resources cannot be fetched from the tekton repository URL !");
            LOG.error(e);
        }
        return resourceAsStream;
    }

    protected static InputStream fetchTektonDashboardResourcesFromURL() {
        InputStream resourceAsStream = null;
        try {
            resourceAsStream = new URL(
                    "https://storage.googleapis.com/tekton-releases/dashboard/latest/release.yaml").openStream();
        } catch (Exception e) {
            LOG.error("The resources cannot be fetched from the tekton dashboard repository URL !");
            LOG.error(e);
        }
        return resourceAsStream;
    }

    protected static InputStream fetchIngressResourcesFromURL(String version) {
        InputStream resourceAsStream = null;
        try {
            if (version == "latest") {
                resourceAsStream = new URL(
                        "https://raw.githubusercontent.com/kubernetes/ingress-nginx/refs/heads/main/deploy/static/provider/kind/deploy.yaml")
                        .openStream();
            } else {
                resourceAsStream = new URL(
                        "https://raw.githubusercontent.com/kubernetes/ingress-nginx/refs/tags/controller-" + version
                                + "/deploy/static/provider/kind/deploy.yaml")
                        .openStream();
            }
        } catch (Exception e) {
            LOG.error("The resources cannot be fetched from the ingress nginx repository URL !");
            LOG.error(e);
        }
        return resourceAsStream;
    }

    protected static void waitTillPodSelectedByLabelsIsReady(Map<String, String> labels, String ns) {
        client.resources(Pod.class)
                .inNamespace(ns)
                .withLabels(labels)
                .waitUntilReady(config.timeOut(), TimeUnit.SECONDS);
        LOG.infof("Pod selected with labels: %s is ready", labels);
    }

    public static void setKubernetesClient(KubernetesClient client) {
        Utils.client = client;
    }

    public static void setConfig(TektonDevServiceConfig devservices) {
        Utils.config = devservices;
    }
}
