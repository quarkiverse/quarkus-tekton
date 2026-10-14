package io.quarkiverse.tekton.task;

import java.util.List;
import java.util.Optional;

/**
 * Describes a Tekton Task: the params and workspaces belong to the task, while the image, script/command, env and
 * working dir belong to each of its steps.
 */
public record TaskDefinition(
        String name,
        Optional<String> description,
        List<ParamDefinition> params,
        List<WorkspaceDefinition> workspaces,
        List<StepDefinition> steps) {

    public TaskDefinition {
        description = description != null ? description : Optional.empty();
        params = params != null ? params : List.of();
        workspaces = workspaces != null ? workspaces : List.of();
        steps = steps != null ? steps : List.of();
    }
}
