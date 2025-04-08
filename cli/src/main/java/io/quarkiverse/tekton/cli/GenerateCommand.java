package io.quarkiverse.tekton.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkiverse.tekton.cli.common.GenerationBaseCommand;
import io.quarkiverse.tekton.common.utils.Projects;
import picocli.CommandLine.Command;

@SuppressWarnings("rawtypes")
@Command(name = "generate", sortOptions = false, mixinStandardHelpOptions = false, header = "Generate Tekton Resources.", headerHeading = "%n", commandListHeading = "%nCommands:%n", synopsisHeading = "%nUsage: ", optionListHeading = "%nOptions:%n")
public class GenerateCommand extends GenerationBaseCommand {

    @Override
    public Properties getBuildSystemProperties() {
        Properties properties = super.getBuildSystemProperties();
        return properties;
    }

    @Override
    public void process(List<HasMetadata> resources) {
        String json = Serialization.asJson(resources);

        // Iterate through the list of the resources to process them individually to include ---
        // as separator between them instead of a list of items
        StringBuilder yamlOutput = new StringBuilder();
        for (HasMetadata resource : resources) {
            String yaml = Serialization.asYaml(resource).trim();
            yamlOutput.append(yaml).append("\n");
        }

        Path root = Projects.getProjectRoot();
        Path dotTekton = root.resolve(".tekton");

        writeStringSafe(dotTekton.resolve("tekton.json"), json);
        writeStringSafe(dotTekton.resolve("tekton.yml"), yamlOutput.toString());
    }
}
