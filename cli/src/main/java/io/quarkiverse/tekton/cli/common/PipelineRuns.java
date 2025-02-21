package io.quarkiverse.tekton.cli.common;

import static io.quarkiverse.tekton.cli.common.Pods.isCompleted;
import static io.quarkiverse.tekton.cli.common.Pods.waitIfPending;

import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.kubernetes.client.dsl.LogWatch;

public final class PipelineRuns {

    private static final String PIPELINERUN_LABEL = "tekton.dev/pipelineRun";

    /**
     * Wait until the pipeline run is ready.
     *
     * @param name the name of the pipeline run
     */
    public static final void waitUntilReady(String name) {
        Clients.tekton().v1beta1().pipelineRuns().withName(name).waitUntilReady(5, TimeUnit.SECONDS);
    }

    /**
     * Log the PipelineRun.
     *
     * @param name the name of the PipelineRun
     */
    public static void log(String name) {
        Clients.kubernetes().pods().withLabel(PIPELINERUN_LABEL, name).list().getItems().forEach(p -> {
            String podName = p.getMetadata().getName();
            waitIfPending(podName);

            p.getSpec().getContainers().forEach(container -> {
                try (LogWatch watch = Clients.kubernetes().pods().withName(p.getMetadata().getName())
                        .inContainer(container.getName())
                        .tailingLines(10).watchLog(System.out)) {
                    Clients.kubernetes().pods().withName(p.getMetadata().getName())
                            .waitUntilCondition(pod -> isCompleted(pod, container.getName()), 10L, TimeUnit.MINUTES);
                } catch (KubernetesClientTimeoutException e) {
                    // ignore
                }
                System.out.flush();
            });
        });
    }
}
