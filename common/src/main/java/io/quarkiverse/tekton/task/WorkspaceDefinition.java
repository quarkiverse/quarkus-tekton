package io.quarkiverse.tekton.task;

import java.util.Optional;

/**
 * Describes a workspace of a Tekton Task.
 */
public record WorkspaceDefinition(String name, Optional<String> mountPath, boolean optional, Optional<String> description) {

    public WorkspaceDefinition {
        mountPath = mountPath != null ? mountPath : Optional.empty();
        description = description != null ? description : Optional.empty();
    }
}
