package io.quarkiverse.tekton.task;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.tekton.v1.ParamSpec;
import io.fabric8.tekton.v1.Step;
import io.fabric8.tekton.v1.Task;
import io.fabric8.tekton.v1.WorkspaceDeclaration;
import io.quarkiverse.tekton.common.utils.Serialization;

class CustomTaskTest {

    private static final String IMAGE = "registry.access.redhat.com/ubi9/ubi-minimal";

    @Test
    void testScriptStep() {
        Task task = CustomTask.create(task("hello", List.of(), List.of(),
                step(null, Optional.of("echo hello"), List.of(), List.of())));

        assertEquals("hello", task.getMetadata().getName());
        Step step = task.getSpec().getSteps().get(0);
        assertEquals("hello", step.getName());
        assertEquals(IMAGE, step.getImage());
        assertEquals("echo hello", step.getScript());
        assertTrue(step.getCommand() == null || step.getCommand().isEmpty());
        assertTrue(Serialization.asYaml(List.of(task)).contains("script: echo hello"));
    }

    @Test
    void testCommandStep() {
        Task task = CustomTask.create(task("list-files", List.of(), List.of(),
                step("ls", Optional.empty(), List.of("sh", "-c"), List.of("ls -la"))));

        Step step = task.getSpec().getSteps().get(0);
        assertEquals("ls", step.getName());
        assertEquals(List.of("sh", "-c"), step.getCommand());
        assertEquals(List.of("ls -la"), step.getArgs());
        assertNull(step.getScript());
    }

    @Test
    void testScriptAndCommand() {
        assertThrows(IllegalArgumentException.class, () -> CustomTask.create(task("hello", List.of(), List.of(),
                step(null, Optional.of("echo hello"), List.of("sh"), List.of()))));
    }

    @Test
    void testNeitherScriptNorCommand() {
        assertThrows(IllegalArgumentException.class, () -> CustomTask.create(task("hello", List.of(), List.of(),
                step(null, Optional.empty(), List.of(), List.of()))));
    }

    @Test
    void testMissingImage() {
        StepDefinition step = new StepDefinition(null, null, Optional.of("echo hello"), null, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> CustomTask.create(task("hello", List.of(), List.of(), step)));
    }

    @Test
    void testNoSteps() {
        assertThrows(IllegalArgumentException.class,
                () -> CustomTask.create(new TaskDefinition("hello", Optional.empty(), null, null, List.of())));
    }

    @Test
    void testMultipleSteps() {
        Task task = CustomTask.create(task("build", List.of(), List.of(),
                step("first", Optional.of("echo 1"), List.of(), List.of()),
                step("second", Optional.of("echo 2"), List.of(), List.of())));

        assertEquals(List.of("first", "second"), task.getSpec().getSteps().stream().map(Step::getName).toList());
    }

    @Test
    void testDuplicatedStepNames() {
        assertThrows(IllegalArgumentException.class, () -> CustomTask.create(task("build", List.of(), List.of(),
                step("same", Optional.of("echo 1"), List.of(), List.of()),
                step("same", Optional.of("echo 2"), List.of(), List.of()))));
    }

    @Test
    void testUnnamedStepAmongMultipleSteps() {
        assertThrows(IllegalArgumentException.class, () -> CustomTask.create(task("build", List.of(), List.of(),
                step(null, Optional.of("echo 1"), List.of(), List.of()),
                step("second", Optional.of("echo 2"), List.of(), List.of()))));
    }

    @Test
    void testEnvKeepsDeclaredOrder() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("ZETA", "z");
        env.put("ALPHA", "a");
        StepDefinition step = new StepDefinition(null, IMAGE, Optional.of("env"), null, null,
                Optional.of("$(workspaces.project-dir.path)"), env);

        Step result = CustomTask.create(task("env", List.of(), List.of(), step)).getSpec().getSteps().get(0);
        assertEquals(List.of("ZETA", "ALPHA"), result.getEnv().stream().map(EnvVar::getName).toList());
        assertEquals("z", result.getEnv().get(0).getValue());
        assertEquals("$(workspaces.project-dir.path)", result.getWorkingDir());
    }

    @Test
    void testParams() {
        Task task = CustomTask.create(task("hello",
                List.of(new ParamDefinition("who", null, Optional.of("world"), Optional.of("Who to greet")),
                        new ParamDefinition("names", "array", Optional.of("a, b,c"), Optional.empty()),
                        new ParamDefinition("labels", "object", Optional.of("app=demo,tier=backend"), Optional.empty()),
                        new ParamDefinition("no-default", "string", Optional.empty(), Optional.empty())),
                List.of(), step(null, Optional.of("echo $(params.who)"), List.of(), List.of())));

        List<ParamSpec> params = task.getSpec().getParams();
        assertEquals("string", params.get(0).getType());
        assertEquals("world", params.get(0).getDefault().getStringVal());
        assertEquals("Who to greet", params.get(0).getDescription());
        assertEquals("array", params.get(1).getType());
        assertEquals(List.of("a", "b", "c"), params.get(1).getDefault().getArrayVal());
        assertEquals("object", params.get(2).getType());
        assertEquals(Map.of("app", "demo", "tier", "backend"), params.get(2).getDefault().getObjectVal());
        assertEquals(List.of("app", "tier"), List.copyOf(params.get(2).getProperties().keySet()));
        assertEquals("string", params.get(2).getProperties().get("app").getType());
        assertNull(params.get(3).getDefault());
    }

    @Test
    void testTektonParamNames() {
        List<String> names = List.of("IMAGE", "PATH_CONTEXT", "maven.repo", "_private", "image-name");
        Task task = CustomTask.create(task("hello",
                names.stream().map(n -> new ParamDefinition(n, null, Optional.empty(), Optional.empty())).toList(),
                List.of(), step(null, Optional.of("echo hello"), List.of(), List.of())));

        assertEquals(names, task.getSpec().getParams().stream().map(ParamSpec::getName).toList());
    }

    @Test
    void testTaskNameWithDots() {
        Task task = CustomTask.create(task("my.task", List.of(), List.of(),
                step(null, Optional.of("echo hello"), List.of(), List.of())));

        assertEquals("my.task", task.getMetadata().getName());
        assertEquals("my-task", task.getSpec().getSteps().get(0).getName());
        assertThrows(IllegalArgumentException.class, () -> CustomTask.create(task("my.task", List.of(), List.of(),
                step("my.step", Optional.of("echo hello"), List.of(), List.of()))));
    }

    @Test
    void testObjectParamRules() {
        StepDefinition step = step(null, Optional.of("echo hello"), List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> CustomTask.create(task("hello",
                List.of(new ParamDefinition("labels", "object", Optional.empty(), Optional.empty())), List.of(), step)));
        assertThrows(IllegalArgumentException.class, () -> CustomTask.create(task("hello",
                List.of(new ParamDefinition("my.labels", "object", Optional.of("app=demo"), Optional.empty())), List.of(),
                step)));
        assertThrows(IllegalArgumentException.class, () -> CustomTask.create(task("hello",
                List.of(new ParamDefinition("labels", "object", Optional.of("app.name=demo"), Optional.empty())),
                List.of(), step)));
    }

    @Test
    void testWorkspaceNamesAndMountPaths() {
        StepDefinition step = step(null, Optional.of("echo hello"), List.of(), List.of());
        Task task = CustomTask.create(task("hello", List.of(),
                List.of(new WorkspaceDefinition("project_dir", Optional.empty(), false, Optional.empty())), step));
        assertEquals("project_dir", task.getSpec().getWorkspaces().get(0).getName());

        assertThrows(IllegalArgumentException.class, () -> CustomTask.create(task("hello", List.of(),
                List.of(new WorkspaceDefinition("source", Optional.of("/mnt/data"), false, Optional.empty()),
                        new WorkspaceDefinition("cache", Optional.of("/mnt/data/"), false, Optional.empty())),
                step)));
        assertThrows(IllegalArgumentException.class, () -> CustomTask.create(task("hello", List.of(),
                List.of(new WorkspaceDefinition("source", Optional.empty(), false, Optional.empty()),
                        new WorkspaceDefinition("cache", Optional.of("/workspace/source"), false, Optional.empty())),
                step)));
        assertThrows(IllegalArgumentException.class, () -> CustomTask.create(task("hello", List.of(),
                List.of(new WorkspaceDefinition("source", Optional.empty(), false, Optional.empty()),
                        new WorkspaceDefinition("source", Optional.of("/mnt/other"), false, Optional.empty())),
                step)));
    }

    @Test
    void testInvalidParamType() {
        assertThrows(IllegalArgumentException.class, () -> CustomTask.create(task("hello",
                List.of(new ParamDefinition("who", "number", Optional.empty(), Optional.empty())), List.of(),
                step(null, Optional.of("echo hello"), List.of(), List.of()))));
    }

    @Test
    void testInvalidObjectDefault() {
        assertThrows(IllegalArgumentException.class, () -> CustomTask.create(task("hello",
                List.of(new ParamDefinition("labels", "object", Optional.of("app"), Optional.empty())), List.of(),
                step(null, Optional.of("echo hello"), List.of(), List.of()))));
    }

    @Test
    void testWorkspaces() {
        Task task = CustomTask.create(task("hello", List.of(),
                List.of(new WorkspaceDefinition("project-dir", Optional.of("/mnt/workspace"), true,
                        Optional.of("The project")),
                        new WorkspaceDefinition("cache", Optional.empty(), false, Optional.empty())),
                step(null, Optional.of("echo hello"), List.of(), List.of())));

        List<WorkspaceDeclaration> workspaces = task.getSpec().getWorkspaces();
        assertEquals("project-dir", workspaces.get(0).getName());
        assertEquals("/mnt/workspace", workspaces.get(0).getMountPath());
        assertEquals(Boolean.TRUE, workspaces.get(0).getOptional());
        assertEquals("The project", workspaces.get(0).getDescription());
        assertEquals("cache", workspaces.get(1).getName());
        assertNull(workspaces.get(1).getMountPath());
        assertNull(workspaces.get(1).getOptional());
    }

    @Test
    void testInvalidNames() {
        StepDefinition step = step(null, Optional.of("echo hello"), List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> CustomTask.create(task("Hello", List.of(), List.of(), step)));
        assertThrows(IllegalArgumentException.class, () -> CustomTask.create(task("hello-", List.of(), List.of(), step)));
        assertThrows(IllegalArgumentException.class, () -> CustomTask.create(task("hello..world", List.of(), List.of(), step)));
        assertThrows(IllegalArgumentException.class,
                () -> CustomTask.create(task("a".repeat(64), List.of(), List.of(), step)));
        assertThrows(IllegalArgumentException.class, () -> CustomTask.create(task("hello",
                List.of(new ParamDefinition("1who", null, Optional.empty(), Optional.empty())), List.of(), step)));
        assertThrows(IllegalArgumentException.class, () -> CustomTask.create(task("hello",
                List.of(new ParamDefinition("who?", null, Optional.empty(), Optional.empty())), List.of(), step)));
        assertThrows(IllegalArgumentException.class, () -> CustomTask.create(task("hello", List.of(), List.of(),
                step("My-Step", Optional.of("echo hello"), List.of(), List.of()))));
    }

    private static TaskDefinition task(String name, List<ParamDefinition> params, List<WorkspaceDefinition> workspaces,
            StepDefinition... steps) {
        return new TaskDefinition(name, Optional.empty(), params, workspaces, List.of(steps));
    }

    private static StepDefinition step(String name, Optional<String> script, List<String> command, List<String> args) {
        return new StepDefinition(name, IMAGE, script, command, args, Optional.empty(), Map.of());
    }
}
