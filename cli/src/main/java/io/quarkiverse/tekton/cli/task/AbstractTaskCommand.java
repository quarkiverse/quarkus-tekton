package io.quarkiverse.tekton.cli.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.tekton.cli.common.Clients;
import io.quarkiverse.tekton.cli.common.GenerationBaseCommand;
import io.quarkiverse.tekton.cli.common.OutputOptionMixin;
import picocli.CommandLine.Mixin;

public abstract class AbstractTaskCommand extends GenerationBaseCommand {

    @Inject
    KubernetesClient kubernetesClient;

    // TODO: Uncomment when we get a new tektonclinet release
    //    @Inject
    //    TektonClient tektonClient;

    @Mixin(name = "output")
    OutputOptionMixin output;

    private Map<String, io.fabric8.tekton.v1.Task> installedV1Tasks = new HashMap<>();
    private Map<String, io.fabric8.tekton.v1beta1.Task> installedV1beta1Tasks = new HashMap<>();

    private Map<String, io.fabric8.tekton.v1.Task> projectV1Tasks = new HashMap<>();
    private Map<String, io.fabric8.tekton.v1beta1.Task> projectV1beta1Tasks = new HashMap<>();

    void readInstalledTasks() {
        try {
            Clients.use(kubernetesClient);
            //Clients.use(tektonClient);

            Clients.tekton().v1().tasks().list().getItems().forEach(t -> {
                installedV1Tasks.put(t.getMetadata().getName(), t);
            });

            Clients.tekton().v1beta1().tasks().list().getItems().forEach(t -> {
                installedV1beta1Tasks.put(t.getMetadata().getName(), t);
            });
        } catch (Exception e) {
            // ignore
        }
    }

    void addInstalledTask(io.fabric8.tekton.v1.Task task) {
        installedV1Tasks.put(task.getMetadata().getName(), task);
    }

    void addInstalledTask(io.fabric8.tekton.v1beta1.Task task) {
        installedV1beta1Tasks.put(task.getMetadata().getName(), task);
    }

    io.fabric8.tekton.v1.Task removeInstalledTask(io.fabric8.tekton.v1.Task task) {
        return installedV1Tasks.remove(task.getMetadata().getName());
    }

    io.fabric8.tekton.v1beta1.Task removeInstalledTask(io.fabric8.tekton.v1beta1.Task task) {
        return installedV1beta1Tasks.remove(task.getMetadata().getName());
    }

    Set<String> getInstalledTaskNames() {
        return Stream.concat(installedV1Tasks.keySet().stream(), installedV1beta1Tasks.keySet().stream())
                .collect(Collectors.toSet());
    }

    boolean isInstalled(String taskName) {
        return installedV1Tasks.keySet().contains(taskName) || installedV1beta1Tasks.keySet().contains(taskName);
    }

    Optional<HasMetadata> getInstalledTask(String taskName) {
        if (installedV1beta1Tasks.containsKey(taskName)) {
            return Optional.of(installedV1beta1Tasks.get(taskName));
        }
        if (installedV1Tasks.containsKey(taskName)) {
            return Optional.of(installedV1Tasks.get(taskName));
        }
        return Optional.empty();
    }

    void readProjectTasks(List<HasMetadata> resources) {
        try {
            for (HasMetadata resource : resources) {
                if (resource instanceof io.fabric8.tekton.v1.Task v1Task) {
                    projectV1Tasks.put(v1Task.getMetadata().getName(), v1Task);
                }

                if (resource instanceof io.fabric8.tekton.v1beta1.Task v1beta1Task) {
                    projectV1beta1Tasks.put(v1beta1Task.getMetadata().getName(), v1beta1Task);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    Set<String> getProjectTaskNames() {
        return Stream.concat(projectV1Tasks.keySet().stream(), projectV1beta1Tasks.keySet().stream())
                .collect(Collectors.toSet());
    }

    boolean isProject(String taskName) {
        return projectV1Tasks.keySet().contains(taskName) || projectV1beta1Tasks.keySet().contains(taskName);
    }

    Optional<HasMetadata> getProjectTask(String taskName) {
        if (projectV1beta1Tasks.containsKey(taskName)) {
            return Optional.of(projectV1beta1Tasks.get(taskName));
        }
        if (projectV1Tasks.containsKey(taskName)) {
            return Optional.of(projectV1Tasks.get(taskName));
        }
        return Optional.empty();
    }

    public List<TaskListItem> getTaskListItems() {
        List<TaskListItem> items = new ArrayList<>();
        for (String taskName : projectV1beta1Tasks.keySet()) {
            items.add(new TaskListItem("tekton.dev/v1beta1", "Task", taskName, isInstalled(taskName), true));
        }

        for (String taskName : projectV1Tasks.keySet()) {
            items.add(new TaskListItem("tekton.dev/v1", "Task", taskName, isInstalled(taskName), true));
        }
        return items;
    }

    public List<TaskListItem> getTaskListItems(Predicate<TaskListItem> predicate) {
        List<TaskListItem> items = new ArrayList<>();
        for (String taskName : projectV1beta1Tasks.keySet()) {
            items.add(new TaskListItem("tekton.dev/v1beta1", "Task", taskName, isInstalled(taskName), true));
        }

        for (String taskName : projectV1Tasks.keySet()) {
            items.add(new TaskListItem("tekton.dev/v1", "Task", taskName, isInstalled(taskName), true));
        }
        return items.stream().filter(predicate).collect(Collectors.toList());
    }

    boolean isTask(HasMetadata resource) {
        if (!"Task".equals(resource.getKind())) {
            return false;
        }
        if (!"tekton.dev/v1beta1".equals(resource.getApiVersion()) && !"tekton.dev/v1".equals(resource.getApiVersion())) {
            return false;
        }
        return true;
    }
}
