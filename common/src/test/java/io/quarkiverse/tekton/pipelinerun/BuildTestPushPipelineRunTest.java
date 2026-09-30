package io.quarkiverse.tekton.pipelinerun;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.fabric8.tekton.v1.Pipeline;
import io.fabric8.tekton.v1.PipelineRun;
import io.fabric8.tekton.v1.WorkspaceBinding;
import io.quarkiverse.tekton.pipeline.BuildTestPushPipeline;

class BuildTestPushPipelineRunTest {

    @Test
    void testWorkspacesAreBoundToGeneratedResources() {
        Pipeline pipeline = BuildTestPushPipeline.create();
        PipelineRun pipelineRun = BuildTestPushPipelineRun.create("my-app", pipeline,
                Optional.of(Map.of("url", "https://github.com/org/my-app.git", "output-image", "quay.io/org/my-app")),
                "my-registry-secret");

        assertEquals("my-app-run", pipelineRun.getMetadata().getName());
        assertEquals("build-test-push", pipelineRun.getSpec().getPipelineRef().getName());

        Map<String, WorkspaceBinding> bindings = pipelineRun.getSpec().getWorkspaces().stream()
                .collect(Collectors.toMap(WorkspaceBinding::getName, w -> w));
        assertEquals(pipeline.getSpec().getWorkspaces().size(), bindings.size());
        assertEquals("my-app-project-pvc", bindings.get("project-dir").getPersistentVolumeClaim().getClaimName());
        assertEquals("my-app-maven-repo-pvc", bindings.get("maven-repo-dir").getPersistentVolumeClaim().getClaimName());
        assertEquals("my-app-maven-settings", bindings.get("maven-settings").getConfigMap().getName());
        assertEquals("my-registry-secret", bindings.get("dockerconfig-secret").getSecret().getSecretName());
    }

    @Test
    void testWorkspacesAreNotAccumulatedAcrossInvocations() {
        Pipeline pipeline = BuildTestPushPipeline.create();
        BuildTestPushPipelineRun.create("my-app", pipeline, Optional.empty());
        PipelineRun pipelineRun = BuildTestPushPipelineRun.create("my-app", pipeline, Optional.empty());

        assertEquals(pipeline.getSpec().getWorkspaces().size(), pipelineRun.getSpec().getWorkspaces().size());
        assertEquals("dockerconfig-secret",
                pipelineRun.getSpec().getWorkspaces().stream().filter(w -> w.getName().equals("dockerconfig-secret"))
                        .findFirst().orElseThrow().getSecret().getSecretName());
    }

    @Test
    void testMissingMandatoryParams() {
        Pipeline pipeline = BuildTestPushPipeline.create();
        List<String> missing = BuildTestPushPipelineRun.missingMandatoryParams(pipeline, Set.of("url"));
        assertEquals(List.of("output-image", "mavenGoals"), missing);
    }
}
