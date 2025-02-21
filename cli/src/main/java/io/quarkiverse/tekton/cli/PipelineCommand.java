package io.quarkiverse.tekton.cli;

import java.util.concurrent.Callable;

import io.quarkiverse.tekton.cli.common.OutputOptionMixin;
import io.quarkiverse.tekton.cli.pipeline.PipelineExec;
import io.quarkiverse.tekton.cli.pipeline.PipelineInstall;
import io.quarkiverse.tekton.cli.pipeline.PipelineList;
import io.quarkiverse.tekton.cli.pipeline.PipelineUninstall;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@SuppressWarnings("rawtypes")
@Command(name = "pipeline", sortOptions = false, mixinStandardHelpOptions = false, header = "Pipeline CLI", subcommands = {
        PipelineExec.class,
        PipelineInstall.class,
        PipelineUninstall.class,
        PipelineList.class })
public class PipelineCommand implements Callable<Integer> {

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
