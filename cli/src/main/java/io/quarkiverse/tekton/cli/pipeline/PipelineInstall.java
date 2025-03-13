package io.quarkiverse.tekton.cli.pipeline;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.quarkiverse.tekton.cli.common.Clients;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "install", header = "Install tekton pipelines.")
public class PipelineInstall extends AbstractPipelineCommand {

    @Parameters(index = "0", arity = "0..1", paramLabel = "pipeline", description = "Pipeline name.")
    Optional<String> pipelineName;

    @Option(names = { "--all" }, description = "Install all plugins in the catalog.")
    boolean all;

    @Option(names = { "-r", "--regenerate" }, description = "Regenerate and reinstall the pipeline.")
    boolean regenerate = false;

    @Override
    public boolean shouldOverwrite() {
        return regenerate;
    }

    @Override
    public void process(List<HasMetadata> resources) {
        if (pipelineName.isEmpty() && !all) {
            throw new IllegalArgumentException("A pipeline name or --all flag must be specified.");
        }

        readInstalledPipelines();
        readProjectPipelines(resources);

        for (String name : getProjectPipelineNames()) {
            if (!all && pipelineName.filter(n -> !n.equals(name)).isPresent()) {
                continue;
            }

            HasMetadata resource = getProjectPipeline(name)
                    .orElseThrow(() -> new IllegalArgumentException("Pipeline " + name + " not found."));
            if (resource instanceof io.fabric8.tekton.v1.Pipeline v1Pipeline) {
                Clients.kubernetes().resource(v1Pipeline).serverSideApply();
                addInstalledPipeline(v1Pipeline);

            } else if (resource instanceof io.fabric8.tekton.v1beta1.Pipeline v1beta1Pipeline) {
                Clients.kubernetes().resource(v1beta1Pipeline).serverSideApply();
                addInstalledPipeline(v1beta1Pipeline);
            }
        }

        System.out.println("Installed pipelines:");
        PipelineTable table = new PipelineTable(getPipelineListItems());
        table.print();
    }
}
