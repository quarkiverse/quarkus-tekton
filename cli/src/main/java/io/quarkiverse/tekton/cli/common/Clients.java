package io.quarkiverse.tekton.cli.common;

import java.util.concurrent.atomic.AtomicReference;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.tekton.client.DefaultTektonClient;
import io.fabric8.tekton.client.TektonClient;

public class Clients {

    private static final KubernetesClient DEFAULT_KUBERNETES = new KubernetesClientBuilder().build();
    private static final TektonClient DEFAULT_TEKTON = new DefaultTektonClient();

    private static final AtomicReference<KubernetesClient> kubernetes = new AtomicReference<>(DEFAULT_KUBERNETES);
    private static final AtomicReference<TektonClient> tekton = new AtomicReference<>(DEFAULT_TEKTON);

    public static KubernetesClient kubernetes() {
        return kubernetes.get();
    }

    public static void use(KubernetesClient kubernetesClient) {
        kubernetes.set(kubernetesClient);
        use(new DefaultTektonClient(kubernetesClient.getConfiguration()));

    }

    public static TektonClient tekton() {
        return tekton.get();
    }

    public static void use(TektonClient tektonClient) {
        tekton.set(tektonClient);
    }
}
