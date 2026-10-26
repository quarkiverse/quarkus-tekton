package io.quarkiverse.tekton.cli.task;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.tekton.v1.Task;
import io.quarkiverse.tekton.cli.common.ApplicationProperties;
import io.quarkiverse.tekton.common.utils.Serialization;
import io.quarkiverse.tekton.task.CustomTask;
import io.quarkiverse.tekton.task.StepDefinition;
import io.quarkiverse.tekton.task.TaskDefinition;
import io.quarkus.devtools.project.QuarkusProjectHelper;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "generate", header = "Generate a custom task, declared in the application.properties of the project.")
public class TaskGenerate extends AbstractTaskCommand {

    private static final Set<String> PROVIDED_TASKS = Set.of("ls", "rm", "git-clone", "maven", "buildah");

    @Parameters(index = "0", paramLabel = "TASK", description = "Task name.")
    String taskName;

    @Option(names = { "-i", "--image" }, required = true, description = "Image of the step running the task.")
    String image;

    @ArgGroup(exclusive = true)
    StepCommand stepCommand;

    static class StepCommand {
        @Option(names = { "-s", "--script" }, required = true, description = "Script executed by the step.")
        String script;

        @Option(names = { "-c",
                "--command" }, required = true, split = ",", description = "Command executed by the step (e.g. sh,-c).")
        List<String> command;
    }

    @Option(names = { "-a",
            "--args" }, split = ",", description = "Arguments passed to the command or script of the step (e.g. -c,ls -la).")
    List<String> args = new ArrayList<>();

    @Option(names = { "--env" }, paramLabel = "KEY=VALUE", description = "Environment variable of the step.")
    List<String> env = new ArrayList<>();

    @Option(names = { "-p",
            "--param" }, paramLabel = "name[:type][=default]", description = "Param of the task (e.g. who=world, files:array=a,b or labels:object).")
    List<String> params = new ArrayList<>();

    @Option(names = {
            "--param-description" }, paramLabel = "name=description", description = "Description of a param.")
    List<String> paramDescriptions = new ArrayList<>();

    @Option(names = {
            "--param-properties" }, paramLabel = "name=key1,key2", description = "Properties (keys) of an object param.")
    List<String> paramProperties = new ArrayList<>();

    @Option(names = { "-w",
            "--workspace" }, paramLabel = "name[:mountPath][:optional]", description = "Workspace of the task (e.g. source, source:/workspace/src or source::optional).")
    List<String> workspaces = new ArrayList<>();

    @Option(names = {
            "--working-dir" }, description = "Working directory of the step (e.g. $(workspaces.project-dir.path)).")
    Optional<String> workingDir = Optional.empty();

    @Option(names = { "-d", "--description" }, description = "Description of the task.")
    Optional<String> description = Optional.empty();

    @Option(names = {
            "--dry-run" }, description = "Print the properties and the task without writing the application.properties.")
    boolean dryRun = false;

    @Override
    public Integer call() {
        TaskDefinition definition = toDefinition();
        // validates the task before writing anything
        Task task = CustomTask.create(definition);
        List<String> lines = ApplicationProperties.taskLines(definition);

        if (dryRun) {
            if (PROVIDED_TASKS.contains(taskName)) {
                output.warn("The custom task " + taskName + " replaces the provided task " + taskName + ".");
            }
            output.out().println(String.join(System.lineSeparator(), lines));
            output.out().println();
            output.out().println(Serialization.asYaml(List.of(task)));
            return ExitCode.OK;
        }

        Path projectRoot = getWorkingDirectory();
        if (QuarkusProjectHelper.detectExistingBuildTool(projectRoot) == null) {
            throw new IllegalStateException("No Maven or Gradle project found in " + projectRoot
                    + ". Please run the command from the root of a Quarkus project.");
        }

        Path applicationProperties = ApplicationProperties.path(projectRoot);
        Optional<String> original = read(applicationProperties);
        boolean updated = ApplicationProperties.writeTask(applicationProperties, taskName, lines);
        output.out().println("Task " + taskName + (updated ? " updated in " : " added to ") + applicationProperties + ".");
        for (String yaml : List.of("application.yaml", "application.yml")) {
            if (Files.exists(applicationProperties.resolveSibling(yaml))) {
                output.warn("The project also has an " + yaml + ": the task is only declared in application.properties.");
            }
        }

        // regenerates the resources, including the task now declared in the application.properties
        try {
            return super.call();
        } catch (RuntimeException e) {
            // the task is only kept when it was generated (e.g. the build fails when the project is not compiled)
            restore(applicationProperties, original);
            output.warn("The resources could not be regenerated: " + applicationProperties
                    + " was restored. Make sure the project is compiled (e.g. mvn compile) and run the command again.");
            throw e;
        }
    }

    private static Optional<String> read(Path file) {
        try {
            return Files.exists(file) ? Optional.of(Files.readString(file, StandardCharsets.UTF_8)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void restore(Path file, Optional<String> content) {
        try {
            if (content.isPresent()) {
                Files.writeString(file, content.get(), StandardCharsets.UTF_8);
            } else {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void process(List<HasMetadata> resources) {
        writeStringSafe(getTektonOutputDir().resolve("tekton.json"), Serialization.asJson(resources));
        writeStringSafe(getTektonOutputDir().resolve("tekton.yml"), Serialization.asYaml(resources));

        Task task = resources.stream()
                .filter(r -> r instanceof Task && taskName.equals(r.getMetadata().getName()))
                .map(Task.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Task " + taskName
                        + " was not generated. Check that the generation of the Tekton resources is enabled (quarkus.tekton.generation.enabled)."));

        output.out().println(Serialization.asYaml(List.of(task)));
        output.out().println("Task " + taskName + " generated in " + getTektonOutputDir().resolve("tekton.yml") + ".");
        output.out().println("Install it using: quarkus tekton task install " + taskName);
        output.out().println("Use it in a pipeline using: quarkus tekton pipeline generate my-pipeline --task git-clone,"
                + taskName);
    }

    TaskDefinition toDefinition() {
        Optional<String> script = Optional.ofNullable(stepCommand).map(c -> c.script);
        List<String> command = Optional.ofNullable(stepCommand).map(c -> c.command).orElse(List.of());
        // sorted as the env configured using quarkus.tekton.task.<name>.env
        TreeMap<String, String> envVars = new TreeMap<>(TaskOptions.parseKeyValues("--env", env));
        StepDefinition step = new StepDefinition(null, image, script, command, args, workingDir, envVars);
        return new TaskDefinition(taskName, description,
                TaskOptions.parseParams(params, paramDescriptions, paramProperties),
                TaskOptions.parseWorkspaces(workspaces), List.of(step));
    }
}
