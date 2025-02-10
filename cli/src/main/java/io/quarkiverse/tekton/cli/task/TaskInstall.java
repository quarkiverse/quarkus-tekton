package io.quarkiverse.tekton.cli.task;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.quarkiverse.tekton.cli.common.Clients;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "install", header = "Install tekton tasks.")
public class TaskInstall extends AbstractTaskCommand {

    @Option(names = {
            "--all" }, defaultValue = "", paramLabel = "all", order = 6, description = "Insall all plugins in the catalog.")
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

        for (String name : getProjectTaskNames()) {
            if (!all && taskName.filter(n -> !n.equals(name)).isPresent()) {
                continue;
            }

            HasMetadata resource = getProjectTask(name)
                    .orElseThrow(() -> new IllegalArgumentException("Task " + name + " not found."));
            if (resource instanceof io.fabric8.tekton.v1.Task v1Task) {
                Clients.kubernetes().resource(v1Task).serverSideApply();
                //                    Clients.tekton().v1().tasks().resource(v1Task).serverSideApply();
                addInstalledTask(v1Task);

            } else if (resource instanceof io.fabric8.tekton.v1beta1.Task v1beta1Task) {
                //                Clients.tekton().v1beta1().tasks().resource(v1beta1Task).serverSideApply();
                Clients.kubernetes().resource(v1beta1Task).serverSideApply();
                addInstalledTask(v1beta1Task);
            }
        }

        System.out.println("Installed tasks:");
        TaskTable table = new TaskTable(getTaskListItems());
        table.print();
    }
}
