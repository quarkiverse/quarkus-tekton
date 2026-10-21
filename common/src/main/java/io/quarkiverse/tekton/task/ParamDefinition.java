package io.quarkiverse.tekton.task;

import java.util.Optional;

/**
 * Describes a param of a Tekton Task. The type defaults to {@code string}.
 * The default value of an {@code array} param is a comma-separated list, the one of an {@code object} param a
 * comma-separated list of {@code key=value} pairs.
 */
public record ParamDefinition(String name, String type, Optional<String> defaultValue, Optional<String> description) {

    public ParamDefinition {
        type = type != null && !type.isBlank() ? type : "string";
        defaultValue = defaultValue != null ? defaultValue : Optional.empty();
        description = description != null ? description : Optional.empty();
    }
}
