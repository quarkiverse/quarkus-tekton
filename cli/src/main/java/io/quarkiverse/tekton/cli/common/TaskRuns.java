package io.quarkiverse.tekton.cli.common;

import static io.quarkiverse.tekton.cli.common.Pods.isCompleted;
import static io.quarkiverse.tekton.cli.common.Pods.waitIfPending;

import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.quarkiverse.tekton.common.utils.Clients;

public final class TaskRuns {

    private static final String TASKRUN_LABEL = "tekton.dev/taskRun";

    /**
     * Wait until the task run is ready.
     *
     * @param name the name of the task run
     */
    public static final void waitUntilReady(String name) {
        Clients.tekton().v1beta1().taskRuns().withName(name).waitUntilReady(5, TimeUnit.SECONDS);
    }

    /**
     * Log the TaskRun.
     *
     * @param name the name of the TaskRun
     */
    public static void log(String name) {
        Clients.kubernetes().pods().withLabel(TASKRUN_LABEL, name).list().getItems().forEach(p -> {
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
