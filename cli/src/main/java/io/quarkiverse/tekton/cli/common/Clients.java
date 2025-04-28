package io.quarkiverse.tekton.cli.common;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.tekton.client.DefaultTektonClient;
import io.fabric8.tekton.client.TektonClient;

public class Clients {

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
}
