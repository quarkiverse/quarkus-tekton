package io.quarkiverse.tekton.cli;

import java.util.concurrent.Callable;

import io.quarkiverse.tekton.cli.common.OutputOptionMixin;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@TopCommand
@Command(name = "tekton", sortOptions = false, mixinStandardHelpOptions = false, header = "Tekton CLI", subcommands = {
        TaskCommand.class,
        PipelineCommand.class,
        GenerateCommand.class })
public class TektonCommand implements Callable<Integer> {

    @Mixin(name = "output")
    OutputOptionMixin output;

    @Spec
    protected CommandSpec spec;

    @Option(names = { "-h", "--help" }, usageHelp = true, description = "Display this help message.")
    public boolean help;

    @Override
    public Integer call() {
        CommandLine entitiesCommand = spec.subcommands().get("generate");
        return entitiesCommand.execute();
    }

    public OutputOptionMixin getOutput() {
        return output;
    }

    public CommandSpec getSpec() {
        return spec;
    }

    public boolean isHelp() {
        return help;
    }
}
