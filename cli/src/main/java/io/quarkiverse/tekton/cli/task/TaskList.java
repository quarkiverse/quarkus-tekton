package io.quarkiverse.tekton.cli.task;

import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import picocli.CommandLine.Command;

@Command(name = "list", header = "List Tekton tasks. ")
public class TaskList extends AbstractTaskCommand {

    @Override
    public boolean shouldOverwrite() {
        return false;
    }

    @Override
    public void process(List<HasMetadata> resources) {
        checkNamespace();
        readInstalledTasks();
        readProjectTasks(resources);

        TaskTable table = new TaskTable(getTaskListItems());
        table.print();
    }
}
