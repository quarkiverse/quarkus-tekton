package io.quarkiverse.tekton.task;

import java.util.List;
import java.util.Optional;

/**
 * Describes a param of a Tekton Task. The type defaults to {@code string}.
 * The default value of an {@code array} param is a comma-separated list, the one of an {@code object} param a
 * comma-separated list of {@code key=value} pairs.
 * The properties (keys) of an {@code object} param are required by Tekton: when they are not given, they are derived
 * from the keys of the default value.
 */
public record ParamDefinition(String name, String type, Optional<String> defaultValue, Optional<String> description,
        List<String> properties) {

    public ParamDefinition {
        type = type != null && !type.isBlank() ? type : "string";
        defaultValue = defaultValue != null ? defaultValue : Optional.empty();
        description = description != null ? description : Optional.empty();
        properties = properties != null ? properties : List.of();
    }

    public ParamDefinition(String name, String type, Optional<String> defaultValue, Optional<String> description) {
        this(name, type, defaultValue, description, List.of());
    }
}
