package io.quarkiverse.tekton.cli.common;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.quarkiverse.tekton.common.utils.Clients;

public class Pods {

    private static final List<String> NON_PENDING_STATES = List.of("Running", "Succeeded", "Completed", "PodCompleted",
            "Failed",
            "Error");
    private static final List<String> COMPLETED_STATES = List.of("Succeeded", "Completed", "PodCompleted", "Failed", "Error");

    public static void waitIfPending(String pod) {
        try {
            // Just wait until the pod is created
            try {
                for (int i = 0; i < 10 && Clients.kubernetes().pods().withName(pod).get() == null; i++) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                //ignore
            }

            Clients.kubernetes().pods().withName(pod).waitUntilCondition(p -> {
                return !isPending(p);
            }, 15, TimeUnit.SECONDS);
        } catch (KubernetesClientTimeoutException e) {
            /*
             * Pod p = Clients.kubernetes().pods().withName(pod).get();
             * if (p != null && p.getStatus() != null && p.getStatus().getConditions() != null) {
             *
             * p.getStatus().getConditions().stream()
             * .filter(c -> c.getReason() != null)
             * .map(c -> c.getReason())
             * .forEach(System.out::println);
             * }
             */
        }
    }

    public static boolean isPending(Pod pod) {
        try {
            if (pod == null || pod.getStatus() == null || pod.getStatus().getConditions() == null
                    || pod.getStatus().getConditions().isEmpty()) {
                return true;
            }
            Optional<String> state = pod.getStatus().getConditions().stream()
                    .filter(c -> c.getReason() != null)
                    .map(c -> c.getReason())
                    .filter(r -> NON_PENDING_STATES.contains(r))
                    .findFirst();
            return !state.isPresent();
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean isCompleted(Pod pod) {
        try {
            if (pod == null || pod.getStatus() == null || pod.getStatus().getConditions() == null
                    || pod.getStatus().getConditions().isEmpty()) {
                return false;
            }
            Optional<String> state = pod.getStatus().getConditions().stream()
                    .filter(c -> c.getReason() != null)
                    .map(c -> c.getReason())
                    .filter(r -> COMPLETED_STATES.contains(r))
                    .findFirst();
            return state.isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isCompleted(Pod pod, String container) {
        try {
            if (pod == null || pod.getStatus() == null || pod.getStatus().getConditions() == null
                    || pod.getStatus().getConditions().isEmpty()) {
                return false;
            }
            Optional<String> state = pod.getStatus().getConditions().stream()
                    .filter(c -> c.getReason() != null)
                    .map(c -> c.getReason())
                    .filter(r -> COMPLETED_STATES.contains(r))
                    .findFirst();

            if (state.isPresent()) {
                return true;
            }

            //Check if we have a terminated container on a running Pod
            return pod.getStatus().getContainerStatuses().stream()
                    .filter(c -> c.getName().equals(container))
                    .map(c -> c.getState())
                    .anyMatch(s -> s.getTerminated() != null);
        } catch (Exception e) {
            return false;
        }
    }
}
