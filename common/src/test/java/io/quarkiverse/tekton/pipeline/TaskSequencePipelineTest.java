package io.quarkiverse.tekton.pipeline;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.fabric8.tekton.v1.ParamSpec;
import io.fabric8.tekton.v1.Pipeline;
import io.fabric8.tekton.v1.PipelineTask;
import io.fabric8.tekton.v1.PipelineWorkspaceDeclaration;
import io.fabric8.tekton.v1.Task;
import io.fabric8.tekton.v1.TaskBuilder;
import io.quarkiverse.tekton.task.GitCloneTask;
import io.quarkiverse.tekton.task.LsTask;
import io.quarkiverse.tekton.task.MavenTask;

class TaskSequencePipelineTest {

    @Test
    void testTasksRunSequentially() {
        Pipeline pipeline = TaskSequencePipeline.create("my-pipeline",
                List.of(GitCloneTask.create(), MavenTask.create(), LsTask.create()));

        assertEquals("my-pipeline", pipeline.getMetadata().getName());
        List<PipelineTask> tasks = pipeline.getSpec().getTasks();
        assertEquals(List.of("git-clone", "maven", "ls"), tasks.stream().map(PipelineTask::getName).toList());
        assertEquals(List.of("git-clone", "maven", "ls"),
                tasks.stream().map(t -> t.getTaskRef().getName()).toList());
        assertTrue(tasks.get(0).getRunAfter() == null || tasks.get(0).getRunAfter().isEmpty());
        assertEquals(List.of("git-clone"), tasks.get(1).getRunAfter());
        assertEquals(List.of("maven"), tasks.get(2).getRunAfter());
    }

    @Test
    void testParamsAndWorkspacesAreExposed() {
        Task ls = LsTask.create();
        Pipeline pipeline = TaskSequencePipeline.create("my-pipeline", List.of(ls, LsTask.create()));

        List<PipelineTask> tasks = pipeline.getSpec().getTasks();
        assertEquals(List.of("ls", "ls-2"), tasks.stream().map(PipelineTask::getName).toList());

        Map<String, ParamSpec> params = pipeline.getSpec().getParams().stream()
                .collect(Collectors.toMap(ParamSpec::getName, p -> p));
        assertEquals(1, params.size());
        assertEquals("string", params.get("args").getType());
        assertEquals("$(params.args)", tasks.get(1).getParams().get(0).getValue().getStringVal());

        List<PipelineWorkspaceDeclaration> workspaces = pipeline.getSpec().getWorkspaces();
        assertEquals(1, workspaces.size());
        assertEquals("project-dir", workspaces.get(0).getName());
        assertEquals(Boolean.TRUE, workspaces.get(0).getOptional());
        assertEquals("project-dir", tasks.get(0).getWorkspaces().get(0).getWorkspace());
    }

    @Test
    void testConflictingParamTypesArePrefixed() {
        Task arrayArgs = new TaskBuilder()
                .withNewMetadata().withName("echo").endMetadata()
                .withNewSpec()
                .addNewParam().withName("args").withType("array").endParam()
                .endSpec()
                .build();
        Pipeline pipeline = TaskSequencePipeline.create("my-pipeline", List.of(LsTask.create(), arrayArgs));

        List<String> names = pipeline.getSpec().getParams().stream().map(ParamSpec::getName).toList();
        assertEquals(List.of("args", "echo-args"), names);
        assertEquals(List.of("$(params.echo-args[*])"),
                pipeline.getSpec().getTasks().get(1).getParams().get(0).getValue().getArrayVal());
    }

    @Test
    void testNoTasks() {
        assertThrows(IllegalArgumentException.class, () -> TaskSequencePipeline.create("my-pipeline", List.of()));
    }
}
