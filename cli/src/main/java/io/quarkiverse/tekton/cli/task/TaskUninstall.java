package io.quarkiverse.tekton.cli.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.quarkiverse.tekton.cli.common.Clients;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "uninstall", header = "Uninstall Tekton tasks.")
public class TaskUninstall extends AbstractTaskCommand {

    @Option(names = {
            "--all" }, defaultValue = "", paramLabel = "all", order = 6, description = "Install all plugins in the catalog.")
    boolean all;

    @Parameters(index = "0", arity = "0..1", paramLabel = "TASK", description = "Task name.")
    Optional<String> taskName;

    @Override
    public boolean shouldOverwrite() {
        return false;
    }

    @Override
    public void process(List<HasMetadata> resources) {
        if (taskName.isEmpty() && !all) {
            throw new IllegalArgumentException("A task name or --all flag must be specified.");
        }

        readInstalledTasks();
        readProjectTasks(resources);
        List<String> uninstalled = new ArrayList<>();

        for (String name : getProjectTaskNames()) {
            if (!all && taskName.filter(n -> !n.equals(name)).isPresent()) {
                continue;
            }

            getProjectTask(name).ifPresentOrElse(resource -> {
                if (resource instanceof io.fabric8.tekton.v1.Task v1Task) {
                    Clients.kubernetes().resource(v1Task).delete();
                    if (removeInstalledTask(v1Task) != null) {
                        uninstalled.add(name);
                    } else {
                        System.out.println("Task " + name + " not installed.");
                    }

                } else if (resource instanceof io.fabric8.tekton.v1beta1.Task v1beta1Task) {
                    Clients.kubernetes().resource(v1beta1Task).delete();
                    if (removeInstalledTask(v1beta1Task) != null) {
                        uninstalled.add(name);
                    } else {
                        System.out.println("Task " + name + " not installed.");
                    }
                }
            }, () -> System.out.println("Task " + name + " not found."));
        }

        if (uninstalled.isEmpty()) {
            output.out().println("No tasks to uninstall.");
            return;
        }
        System.out.println("Uninstalled tasks:");
        TaskTable table = new TaskTable(getTaskListItems(t -> uninstalled.contains(t.getName())));
        table.print();
    }
}
