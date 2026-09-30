package io.quarkiverse.tekton.pipelinerun;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder;
import io.fabric8.tekton.v1.*;
import io.quarkiverse.tekton.cm.MavenSettingsCm;
import io.quarkiverse.tekton.common.utils.Params;
import io.quarkiverse.tekton.pvc.MavenRepoPvc;
import io.quarkiverse.tekton.pvc.ProjectWorkspacePvc;

public class BuildTestPushPipelineRun {
    private static final Logger log = LoggerFactory.getLogger(BuildTestPushPipelineRun.class);

    public static final String PROJECT_DIR_WORKSPACE = "project-dir";
    public static final String MAVEN_REPO_DIR_WORKSPACE = "maven-repo-dir";
    public static final String MAVEN_SETTINGS_WORKSPACE = "maven-settings";
    public static final String DOCKERCONFIG_SECRET_WORKSPACE = "dockerconfig-secret";
    public static final String DEFAULT_REGISTRY_AUTH_SECRET = "dockerconfig-secret";

    public static PipelineRun create(String projectName, Pipeline pipeline, Optional<Map<String, String>> pipelineRunArgs) {
        return create(projectName, pipeline, pipelineRunArgs, DEFAULT_REGISTRY_AUTH_SECRET);
    }

    public static PipelineRun create(String projectName, Pipeline pipeline, Optional<Map<String, String>> pipelineRunArgs,
            String registryAuthSecret) {
        if (pipelineRunArgs.isEmpty() || pipelineRunArgs.get().isEmpty()) {
            log.warn(
                    "The pipelinerun arguments cannot be empty. Set the property: quarkus.tekton.pipelinerun.params with the mandatory pipeline parameters");
            showThePipelineParams(pipeline);
        } else if (!missingMandatoryParams(pipeline, pipelineRunArgs.get().keySet()).isEmpty()) {
            log.warn("Some mandatory parameters are missing: {}",
                    missingMandatoryParams(pipeline, pipelineRunArgs.get().keySet()));
            showThePipelineParams(pipeline);
        }

        // Bind the pipeline workspaces to the resources generated for the project (PVCs, ConfigMap) and to the
        // registry auth secret that we expect to exist on the cluster
        List<WorkspaceBinding> workspaceBindings = new ArrayList<>();
        pipeline.getSpec().getWorkspaces().forEach(w -> workspaceBindings
                .add(workspaceBindingFor(projectName, w.getName(), registryAuthSecret)));

        // Convert the user's arguments to the pipelinerun params
        List<Param> params = new ArrayList<>();
        if (pipelineRunArgs.isPresent()) {
            params = Params.create(pipelineRunArgs.get());
        }

        PipelineRun pipelineRun = new PipelineRunBuilder()
                .withNewMetadata()
                .withName(projectName + "-run")
                .endMetadata()
                .withNewSpec()
                .withNewPipelineRef()
                .withName(pipeline.getMetadata().getName())
                .endPipelineRef()
                .withWorkspaces(workspaceBindings)
                .withParams(params)
                .endSpec()
                .build();
        return pipelineRun;
    }

    public static WorkspaceBinding workspaceBindingFor(String projectName, String workspaceName, String registryAuthSecret) {
        WorkspaceBindingBuilder builder = new WorkspaceBindingBuilder().withName(workspaceName);
        switch (workspaceName) {
            case PROJECT_DIR_WORKSPACE:
                return builder.withNewPersistentVolumeClaim(ProjectWorkspacePvc.create(projectName).getMetadata().getName(),
                        false).build();
            case MAVEN_REPO_DIR_WORKSPACE:
                return builder.withNewPersistentVolumeClaim(MavenRepoPvc.create(projectName).getMetadata().getName(), false)
                        .build();
            case MAVEN_SETTINGS_WORKSPACE:
                return builder.withConfigMap(new ConfigMapVolumeSourceBuilder()
                        .withName(MavenSettingsCm.create(projectName).getMetadata().getName()).build()).build();
            case DOCKERCONFIG_SECRET_WORKSPACE:
                return builder.withSecret(new SecretVolumeSourceBuilder().withSecretName(registryAuthSecret).build()).build();
            default:
                return builder.withEmptyDir(new EmptyDirVolumeSourceBuilder().build()).build();
        }
    }

    public static boolean hasValue(ParamValue paramValue) {
        // TODO: Can we return null if the paramValue is null. To be reviewed
        if (paramValue == null) {
            return false;
        }
        return paramValue.getStringVal() != null || (paramValue.getObjectVal() != null && !paramValue.getObjectVal().isEmpty())
                || (paramValue.getArrayVal() != null && !paramValue.getArrayVal().isEmpty());
    }

    public static long numberOfParamsWithoutDefaultValue(Pipeline pipeline) {
        return pipeline.getSpec().getParams().stream()
                .filter(p -> !hasValue(p.getDefault())) // Filter out Params where hasValue is false
                .count();
    }

    public static List<String> missingMandatoryParams(Pipeline pipeline, Set<String> providedParams) {
        return pipeline.getSpec().getParams().stream()
                .filter(p -> !hasValue(p.getDefault()))
                .map(ParamSpec::getName)
                .filter(n -> !providedParams.contains(n))
                .toList();
    }

    public static void showThePipelineParams(Pipeline pipeline) {
        pipeline.getSpec().getParams().forEach(p -> {
            String defaultLog = p.getDefault() != null ? ", default: " + p.getDefault().getStringVal() : "";
            log.warn("Name: {}, description: {}, type: {}{}",
                    p.getName(),
                    p.getDescription(),
                    p.getType(),
                    defaultLog);
        });
    }

}
