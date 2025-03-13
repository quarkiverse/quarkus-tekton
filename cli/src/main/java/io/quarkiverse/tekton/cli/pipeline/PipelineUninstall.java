package io.quarkiverse.tekton.cli.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.quarkiverse.tekton.cli.common.Clients;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "uninstall", header = "Uninstall Tekton pipelines.")
public class PipelineUninstall extends AbstractPipelineCommand {

    @Option(names = {
            "--all" }, defaultValue = "", paramLabel = "all", order = 6, description = "Uninstall all the pipelines.")
    boolean all;

    @Parameters(index = "0", arity = "0..1", paramLabel = "pipeline", description = "Pipeline name.")
    Optional<String> pipelineName;

    @Override
    public boolean shouldOverwrite() {
        return false;
    }

    @Override
    public void process(List<HasMetadata> resources) {
        if (pipelineName.isEmpty() && !all) {
            throw new IllegalArgumentException("A pipeline name or --all flag must be specified.");
        }

        readInstalledPipelines();
        readProjectPipelines(resources);
        List<String> uninstalled = new ArrayList<>();

        for (String name : getProjectPipelineNames()) {
            if (!all && pipelineName.filter(n -> !n.equals(name)).isPresent()) {
                continue;
            }

            getProjectPipeline(name).ifPresentOrElse(resource -> {
                if (resource instanceof io.fabric8.tekton.v1.Pipeline v1Pipeline) {
                    Clients.kubernetes().resource(v1Pipeline).delete();
                    if (removeInstalledPipeline(v1Pipeline) != null) {
                        uninstalled.add(name);
                    } else {
                        System.out.println("Pipeline " + name + " not installed.");
                    }

                } else if (resource instanceof io.fabric8.tekton.v1beta1.Pipeline v1beta1Pipeline) {
                    Clients.kubernetes().resource(v1beta1Pipeline).delete();
                    if (removeInstalledPipeline(v1beta1Pipeline) != null) {
                        uninstalled.add(name);
                    } else {
                        System.out.println("Pipeline " + name + " not installed.");
                    }
                }
            }, () -> System.out.println("Pipeline " + name + " not found."));
        }

        if (uninstalled.isEmpty()) {
            output.out().println("No pipelines to uninstall.");
            return;
        }
        System.out.println("Uninstalled pipelines:");
        PipelineTable table = new PipelineTable(getPipelineListItems(t -> uninstalled.contains(t.getName())));
        table.print();
    }
}
