package io.quarkiverse.tekton.cli.pipeline;

import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import picocli.CommandLine.Command;

@Command(name = "list", header = "List qton pipelines. ")
public class PipelineList extends AbstractPipelineCommand {

    @Override
    public boolean shouldOverwrite() {
        return false;
    }

    @Override
    public void process(List<HasMetadata> resources) {
        readInstalledPipelines();
        readProjectPipelines(resources);

        PipelineTable table = new PipelineTable(getPipelineListItems());
        table.print();
    }
}
