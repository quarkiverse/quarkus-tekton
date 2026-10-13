package io.quarkiverse.tekton.cli.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.tekton.v1.Pipeline;
import io.fabric8.tekton.v1.Task;
import io.quarkiverse.tekton.common.utils.Serialization;
import io.quarkiverse.tekton.pipeline.TaskSequencePipeline;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "generate", header = "Generate a Tekton pipeline running the given tasks sequentially.")
public class PipelineGenerate extends AbstractPipelineCommand {

    @Parameters(index = "0", paramLabel = "pipeline", description = "Pipeline name.")
    String pipelineName;

    @Option(names = { "-t",
            "--task" }, required = true, split = ",", description = "Name of the task(s) to run, in order (e.g. git-clone,maven,buildah).")
    List<String> taskNames = new ArrayList<>();

    @Option(names = { "-r",
            "--regenerate" }, description = "Regenerate the project's resources before generating the pipeline.")
    boolean regenerate = false;

    @Override
    public boolean shouldOverwrite() {
        return regenerate;
    }

    @Override
    public void process(List<HasMetadata> resources) {
        Map<String, Task> projectTasks = new LinkedHashMap<>();
        for (HasMetadata resource : resources) {
            if (resource instanceof Task task) {
                projectTasks.put(task.getMetadata().getName(), task);
            }
        }

        List<Task> tasks = new ArrayList<>();
        for (String taskName : taskNames) {
            Task task = projectTasks.get(taskName);
            if (task == null) {
                throw new IllegalArgumentException("Task " + taskName + " not found. Available tasks: "
                        + String.join(", ", projectTasks.keySet()) + ".");
            }
            tasks.add(task);
        }

        Pipeline pipeline = TaskSequencePipeline.create(pipelineName, tasks);

        List<HasMetadata> updated = new ArrayList<>(resources);
        updated.removeIf(r -> isPipeline(r) && pipelineName.equals(r.getMetadata().getName()));
        updated.add(pipeline);

        writeStringSafe(getTektonOutputDir().resolve("tekton.json"), Serialization.asJson(updated));
        writeStringSafe(getTektonOutputDir().resolve("tekton.yml"), Serialization.asYaml(updated));

        output.out().println(Serialization.asYaml(List.of(pipeline)));
        output.out().println("Pipeline " + pipelineName + " generated in " + getTektonOutputDir().resolve("tekton.yml")
                + ". Install it using: quarkus tekton pipeline install " + pipelineName);
    }
}
