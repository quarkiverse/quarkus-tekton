package io.quarkiverse.tekton.cli.common;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.tekton.client.DefaultTektonClient;
import io.fabric8.tekton.client.TektonClient;

public class Clients {

    private static final String TEKTON_API_GROUP = "tekton.dev";
    private static final String TEKTON_INSTALL_URL = "https://tekton.dev/docs/installation/pipelines/";

    private static KubernetesClient DEFAULT_KUBERNETES;
    private static TektonClient DEFAULT_TEKTON;
    private static String USER_NAMESPACE;
    private static KubernetesClient kubernetesClient;

    public static void use(KubernetesClient kubeclient) {
        kubernetesClient = kubeclient;
    }

    public static void setNamespace(String namespace) {
        USER_NAMESPACE = namespace;
    }

    public static String getNamespace() {
        return USER_NAMESPACE;
    }

    public static KubernetesClient kubernetes() {
        if (DEFAULT_KUBERNETES == null) {
            DEFAULT_KUBERNETES = new KubernetesClientBuilder().build();
        }
        return DEFAULT_KUBERNETES;
    }

    public static TektonClient tekton() {
        if (DEFAULT_TEKTON == null) {
            DEFAULT_TEKTON = new DefaultTektonClient(kubernetesClient);
        }
        return DEFAULT_TEKTON;
    }

    /**
     * Ensures that Tekton is installed on the cluster, i.e. that the {@code tekton.dev} API group is served.
     *
     * @throws IllegalStateException if Tekton is not installed
     */
    public static void checkTektonInstalled() {
        KubernetesClient client = kubernetes();
        if (client.getApiGroup(TEKTON_API_GROUP) == null) {
            throw new IllegalStateException("Tekton is not installed on the cluster " + client.getMasterUrl()
                    + " (API group '" + TEKTON_API_GROUP + "' not found). Please install Tekton Pipelines: "
                    + TEKTON_INSTALL_URL);
        }
    }
}
