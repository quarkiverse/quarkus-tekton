package io.quarkiverse.tekton.pipelinerun;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.tekton.v1.*;
import io.quarkiverse.tekton.common.utils.Params;
import io.quarkiverse.tekton.common.utils.WorkspaceBindings;

public class BuildTestPushPipelineRun {
    private static final Logger log = LoggerFactory.getLogger(BuildTestPushPipelineRun.class);
    private static List<WorkspaceBinding> workspaceBindings = new ArrayList<>();
    private static Map<String, Param> parameters = new HashMap<>();

    // TODO: The pipelineRunArgs should be passed as a string property and content split into an array
    private static List<String> pipelineRunArgs = new ArrayList<>();

    public static PipelineRun create(String projectName, Pipeline pipeline, Optional<Map<String, String>> pipelineRunArgs) {
        if (!pipelineRunArgs.isPresent()) {
            log.warn(
                    "The pipelinerun arguments cannot be empty. Set the property: quarkus.tekton.pipelinerun.params with the mandatory pipeline parameters");
            showThePipelineParams(pipeline);
        } else if (pipelineRunArgs.get().size() < numberOfParamsWithoutDefaultValue(pipeline)) {
            log.warn("Some mandatory parameters are missing !");
            showThePipelineParams(pipeline);
        }

        pipeline.getSpec().getWorkspaces().forEach(w -> {
            String workspaceName = w.getName();

            /**
             * TODO: As documented on the PR - https://github.com/quarkiverse/quarkus-tekton/pull/31, the code hereafter should
             * be
             * reviewed as it must only be executed at runtime when a cluster exists like resources: pipeline(run), pvc,
             * secrets, configmaps
             *
             * WorkspaceBindings.forName(projectName, workspaceName)
             * .or(() -> !Boolean.TRUE.equals(w.getOptional())
             * ? WorkspaceBindings.forEmpty(projectName, workspaceName)
             * : Optional.empty())
             * .ifPresent(workspaceBindings::add);
             */
            WorkspaceBindings.forEmpty(projectName, workspaceName).ifPresent(workspaceBindings::add);

        });

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
