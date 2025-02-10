package io.quarkiverse.tekton.cli;

import java.util.concurrent.Callable;

import io.quarkiverse.tekton.cli.common.OutputOptionMixin;
import io.quarkiverse.tekton.cli.task.TaskExec;
import io.quarkiverse.tekton.cli.task.TaskInstall;
import io.quarkiverse.tekton.cli.task.TaskList;
import io.quarkiverse.tekton.cli.task.TaskUninstall;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@SuppressWarnings("rawtypes")
@Command(name = "task", sortOptions = false, mixinStandardHelpOptions = false, header = "Task CLI", subcommands = {
        TaskExec.class,
        TaskInstall.class,
        TaskUninstall.class,
        TaskList.class })
public class TaskCommand implements Callable<Integer> {

    @Mixin(name = "output")
    OutputOptionMixin output;

    @Spec
    protected CommandSpec spec;

    @Option(names = { "-h", "--help" }, usageHelp = true, description = "Display this help message.")
    public boolean help;

    @Override
    public Integer call() throws Exception {
        CommandLine entitiesCommand = spec.subcommands().get("list");
        return entitiesCommand.execute();
    }
}
