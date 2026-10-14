package io.quarkiverse.tekton.task;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Describes a step of a Tekton Task. Either a script or a command (with optional args) is expected.
 * When the task has a single step without a name, the step is named after the task.
 */
public record StepDefinition(
        String name,
        String image,
        Optional<String> script,
        List<String> command,
        List<String> args,
        Optional<String> workingDir,
        Map<String, String> env) {

    public StepDefinition {
        script = script != null ? script : Optional.empty();
        command = command != null ? command : List.of();
        args = args != null ? args : List.of();
        workingDir = workingDir != null ? workingDir : Optional.empty();
        env = env != null ? env : Map.of();
    }
}
