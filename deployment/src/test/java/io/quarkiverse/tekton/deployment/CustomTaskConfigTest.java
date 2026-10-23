package io.quarkiverse.tekton.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.tekton.v1.ParamSpec;
import io.fabric8.tekton.v1.Step;
import io.fabric8.tekton.v1.Task;
import io.quarkiverse.tekton.common.utils.Serialization;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

class CustomTaskConfigTest {

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName("custom-task")
            .setApplicationVersion("0.1-SNAPSHOT")
            .setRun(false)
            .setLogRecordPredicate(r -> TektonProcessor.class.getName().equals(r.getLoggerName()))
            .overrideConfigKey("quarkus.tekton.task.hello.description", "Say hello")
            .overrideConfigKey("quarkus.tekton.task.hello.image", "registry.access.redhat.com/ubi9/ubi-minimal")
            .overrideConfigKey("quarkus.tekton.task.hello.script", "echo \"Hello $(params.who) from $GREETING_ENV\"")
            .overrideConfigKey("quarkus.tekton.task.hello.env.GREETING_ENV", "tekton")
            .overrideConfigKey("quarkus.tekton.task.hello.env.A_FIRST_ENV", "first")
            .overrideConfigKey("quarkus.tekton.task.hello.param.who.default", "world")
            .overrideConfigKey("quarkus.tekton.task.hello.param.who.description", "Who to greet")
            .overrideConfigKey("quarkus.tekton.task.hello.param.labels.type", "object")
            .overrideConfigKey("quarkus.tekton.task.hello.param.labels.properties", "app,tier")
            .overrideConfigKey("quarkus.tekton.task.hello.workspace.project-dir.optional", "true")
            .overrideConfigKey("quarkus.tekton.task.hello.working-dir", "$(workspaces.project-dir.path)")
            .overrideConfigKey("quarkus.tekton.task.maven.image", "registry.access.redhat.com/ubi9/openjdk-21")
            .overrideConfigKey("quarkus.tekton.task.maven.command", "mvn")
            .overrideConfigKey("quarkus.tekton.task.maven.args", "-B,package");

    @ProdBuildResults
    ProdModeTestResults results;

    @Test
    void testCustomTask() throws IOException {
        Task hello = task("hello");

        assertEquals("Say hello", hello.getSpec().getDescription());
        Step step = hello.getSpec().getSteps().get(0);
        assertEquals("hello", step.getName());
        assertEquals("registry.access.redhat.com/ubi9/ubi-minimal", step.getImage());
        assertEquals("echo \"Hello $(params.who) from $GREETING_ENV\"", step.getScript());
        assertEquals("$(workspaces.project-dir.path)", step.getWorkingDir());
        assertEquals(List.of("A_FIRST_ENV", "GREETING_ENV"), step.getEnv().stream().map(EnvVar::getName).toList());

        List<ParamSpec> params = hello.getSpec().getParams();
        assertEquals(List.of("labels", "who"), params.stream().map(ParamSpec::getName).toList());
        assertEquals("object", params.get(0).getType());
        assertEquals(List.of("app", "tier"), List.copyOf(params.get(0).getProperties().keySet()));
        assertEquals("string", params.get(1).getType());
        assertEquals("world", params.get(1).getDefault().getStringVal());
        assertEquals("Who to greet", params.get(1).getDescription());

        assertEquals("project-dir", hello.getSpec().getWorkspaces().get(0).getName());
        assertEquals(Boolean.TRUE, hello.getSpec().getWorkspaces().get(0).getOptional());
    }

    @Test
    void testCustomTaskReplacesProvidedTask() throws IOException {
        List<Task> mavenTasks = tasks().stream().filter(t -> "maven".equals(t.getMetadata().getName())).toList();
        assertEquals(1, mavenTasks.size());
        Step step = mavenTasks.get(0).getSpec().getSteps().get(0);
        assertEquals("registry.access.redhat.com/ubi9/openjdk-21", step.getImage());
        assertEquals(List.of("mvn"), step.getCommand());
        assertEquals(List.of("-B", "package"), step.getArgs());

        // the other provided tasks are kept
        assertTrue(tasks().stream().anyMatch(t -> "git-clone".equals(t.getMetadata().getName())));

        assertTrue(results.getRetainedBuildLogRecords().stream()
                .map(r -> String.format(r.getMessage(), r.getParameters()))
                .anyMatch(m -> m.contains("replaces the provided task maven")));
    }

    private Task task(String name) throws IOException {
        return tasks().stream().filter(t -> name.equals(t.getMetadata().getName())).findFirst()
                .orElseThrow(() -> new AssertionError("Task " + name + " not found"));
    }

    private List<Task> tasks() throws IOException {
        Path tektonYaml = results.getBuildDir().resolve("kubernetes").resolve("tekton.yaml");
        assertTrue(Files.exists(tektonYaml), tektonYaml + " not found");
        try (InputStream is = Files.newInputStream(tektonYaml)) {
            List<HasMetadata> resources = Serialization.unmarshalAsList(is).getItems();
            return resources.stream().filter(Task.class::isInstance).map(Task.class::cast).toList();
        }
    }
}
