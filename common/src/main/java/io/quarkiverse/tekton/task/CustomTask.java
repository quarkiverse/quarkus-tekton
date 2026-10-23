package io.quarkiverse.tekton.task;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.tekton.v1.ParamSpec;
import io.fabric8.tekton.v1.ParamSpecBuilder;
import io.fabric8.tekton.v1.ParamValue;
import io.fabric8.tekton.v1.PropertySpec;
import io.fabric8.tekton.v1.Step;
import io.fabric8.tekton.v1.StepBuilder;
import io.fabric8.tekton.v1.Task;
import io.fabric8.tekton.v1.TaskBuilder;
import io.fabric8.tekton.v1.WorkspaceDeclaration;
import io.fabric8.tekton.v1.WorkspaceDeclarationBuilder;

/**
 * Creates a Task from a {@link TaskDefinition}, applying the same name validations as Tekton:
 * <ul>
 * <li>the task name is a DNS-1123 subdomain of at most 63 characters,</li>
 * <li>step names are DNS-1123 labels,</li>
 * <li>string and array param names contain alphanumeric characters, '-', '_' or '.' and start with a letter or '_'
 * (object param names and keys cannot contain '.'),</li>
 * <li>workspace names and mount paths are unique.</li>
 * </ul>
 */
public class CustomTask {

    private static final String DNS_1123_LABEL_FORMAT = "[a-z0-9]([-a-z0-9]*[a-z0-9])?";
    private static final Pattern DNS_1123_LABEL = Pattern.compile(DNS_1123_LABEL_FORMAT);
    private static final Pattern DNS_1123_SUBDOMAIN = Pattern
            .compile(DNS_1123_LABEL_FORMAT + "(\\." + DNS_1123_LABEL_FORMAT + ")*");
    private static final int NAME_MAX_LENGTH = 63;
    private static final Pattern PARAM_NAME = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_.-]*");
    private static final Pattern OBJECT_PARAM_NAME = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_-]*");
    private static final Set<String> PARAM_TYPES = Set.of("string", "array", "object");

    public static Task create(TaskDefinition def) {
        if (def == null) {
            throw new IllegalArgumentException("A task definition is needed to create a task.");
        }
        String taskName = def.name();
        if (taskName == null || taskName.length() > NAME_MAX_LENGTH || !DNS_1123_SUBDOMAIN.matcher(taskName).matches()) {
            throw new IllegalArgumentException("Invalid task name: " + taskName
                    + ". It should be a DNS-1123 subdomain (lowercase alphanumeric characters, '-' or '.', starting and ending with an alphanumeric character) of at most "
                    + NAME_MAX_LENGTH + " characters.");
        }

        if (def.steps().isEmpty()) {
            throw new IllegalArgumentException("Task " + taskName + " needs at least one step.");
        }

        return new TaskBuilder()
                .withNewMetadata()
                .withName(taskName)
                .endMetadata()
                .withNewSpec()
                .withDescription(def.description().orElse(null))
                .withParams(params(taskName, def.params()))
                .withWorkspaces(workspaces(taskName, def.workspaces()))
                .withSteps(steps(taskName, def.steps()))
                .endSpec()
                .build();
    }

    private static List<ParamSpec> params(String taskName, List<ParamDefinition> params) {
        List<ParamSpec> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (ParamDefinition param : params) {
            if (!PARAM_TYPES.contains(param.type())) {
                throw new IllegalArgumentException("Param " + param.name() + " of task " + taskName + " has type "
                        + param.type() + ", expected one of: string, array, object.");
            }
            validateParamName(param);
            if (!names.add(param.name())) {
                throw new IllegalArgumentException("Param " + param.name() + " is declared twice in task " + taskName + ".");
            }
            ParamValue defaultValue = param.defaultValue().map(v -> defaultValue(param, v)).orElse(null);
            ParamSpecBuilder spec = new ParamSpecBuilder()
                    .withName(param.name())
                    .withType(param.type())
                    .withDescription(param.description().orElse(null))
                    .withDefault(defaultValue);
            if ("object".equals(param.type())) {
                spec.withProperties(properties(taskName, param, defaultValue));
            } else if (!param.properties().isEmpty()) {
                throw new IllegalArgumentException("Param " + param.name() + " of task " + taskName + " has type "
                        + param.type() + ", only object params can declare properties.");
            }
            result.add(spec.build());
        }
        return result;
    }

    /**
     * Tekton requires the properties of object params: they are the declared ones, or the keys of the default value.
     */
    private static Map<String, PropertySpec> properties(String taskName, ParamDefinition param, ParamValue defaultValue) {
        Set<String> keys = new LinkedHashSet<>();
        for (String property : param.properties()) {
            validateObjectKey(param, property);
            if (!keys.add(property)) {
                throw new IllegalArgumentException("Property " + property + " of object param " + param.name()
                        + " is declared twice in task " + taskName + ".");
            }
        }
        if (defaultValue != null) {
            Set<String> defaultKeys = defaultValue.getObjectVal().keySet();
            if (keys.isEmpty()) {
                keys.addAll(defaultKeys);
            } else {
                for (String key : defaultKeys) {
                    if (!keys.contains(key)) {
                        throw new IllegalArgumentException("Key " + key + " of the default value of object param "
                                + param.name() + " in task " + taskName + " is not a declared property. Declared properties: "
                                + String.join(", ", keys) + ".");
                    }
                }
            }
        }
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("Object param " + param.name() + " of task " + taskName
                    + " needs properties or a default value (key=value pairs) to declare its properties.");
        }

        Map<String, PropertySpec> properties = new LinkedHashMap<>();
        keys.forEach(key -> properties.put(key, new PropertySpec("string")));
        return properties;
    }

    private static ParamValue defaultValue(ParamDefinition param, String value) {
        return switch (param.type()) {
            case "array" -> new ParamValue(split(value));
            case "object" -> {
                Map<String, String> entries = new LinkedHashMap<>();
                for (String entry : split(value)) {
                    int index = entry.indexOf('=');
                    if (index <= 0) {
                        throw new IllegalArgumentException("Default value of object param " + param.name()
                                + " should be a comma-separated list of key=value pairs, got: " + value + ".");
                    }
                    String key = entry.substring(0, index).trim();
                    validateObjectKey(param, key);
                    entries.put(key, entry.substring(index + 1).trim());
                }
                yield new ParamValue(entries);
            }
            default -> new ParamValue(value);
        };
    }

    private static List<WorkspaceDeclaration> workspaces(String taskName, List<WorkspaceDefinition> workspaces) {
        List<WorkspaceDeclaration> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Set<Path> mountPaths = new HashSet<>();
        for (WorkspaceDefinition workspace : workspaces) {
            if (workspace.name() == null || workspace.name().isBlank()) {
                throw new IllegalArgumentException("Workspaces of task " + taskName + " need a name.");
            }
            if (!names.add(workspace.name())) {
                throw new IllegalArgumentException(
                        "Workspace " + workspace.name() + " is declared twice in task " + taskName + ".");
            }
            // Tekton mounts a workspace under /workspace/<name> when no mount path is given
            Path mountPath = Path.of(workspace.mountPath().orElse("/workspace/" + workspace.name())).normalize();
            if (!mountPaths.add(mountPath)) {
                throw new IllegalArgumentException("Mount path " + mountPath + " of workspace " + workspace.name()
                        + " is already used by another workspace of task " + taskName + ".");
            }
            result.add(new WorkspaceDeclarationBuilder()
                    .withName(workspace.name())
                    .withDescription(workspace.description().orElse(null))
                    .withMountPath(workspace.mountPath().orElse(null))
                    .withOptional(workspace.optional() ? true : null)
                    .build());
        }
        return result;
    }

    private static List<Step> steps(String taskName, List<StepDefinition> steps) {
        List<Step> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (StepDefinition step : steps) {
            String stepName = step.name();
            if ((stepName == null || stepName.isBlank()) && steps.size() == 1) {
                // step names are DNS-1123 labels, which cannot contain the dots allowed in task names
                stepName = taskName.replace('.', '-');
            }
            if (stepName == null || stepName.length() > NAME_MAX_LENGTH || !DNS_1123_LABEL.matcher(stepName).matches()) {
                throw new IllegalArgumentException("Invalid step name: " + stepName + " in task " + taskName
                        + ". It should be a DNS-1123 label (lowercase alphanumeric characters or '-', starting and ending with an alphanumeric character) of at most "
                        + NAME_MAX_LENGTH + " characters.");
            }
            if (!names.add(stepName)) {
                throw new IllegalArgumentException("Step " + stepName + " is declared twice in task " + taskName + ".");
            }
            if (step.image() == null || step.image().isBlank()) {
                throw new IllegalArgumentException("Step " + stepName + " of task " + taskName + " needs an image.");
            }
            if (step.script().isPresent() && !step.command().isEmpty()) {
                throw new IllegalArgumentException(
                        "Step " + stepName + " of task " + taskName + " declares both a script and a command.");
            }
            if (step.script().isEmpty() && step.command().isEmpty()) {
                throw new IllegalArgumentException(
                        "Step " + stepName + " of task " + taskName + " needs either a script or a command.");
            }

            List<EnvVar> env = new ArrayList<>();
            step.env().forEach((name, value) -> env.add(new EnvVarBuilder().withName(name).withValue(value).build()));

            result.add(new StepBuilder()
                    .withName(stepName)
                    .withImage(step.image())
                    .withWorkingDir(step.workingDir().orElse(null))
                    .withScript(step.script().orElse(null))
                    .withCommand(step.command().isEmpty() ? null : step.command())
                    .withArgs(step.args().isEmpty() ? null : step.args())
                    .withEnv(env.isEmpty() ? null : env)
                    .build());
        }
        return result;
    }

    private static void validateParamName(ParamDefinition param) {
        if ("object".equals(param.type())) {
            if (param.name() == null || !OBJECT_PARAM_NAME.matcher(param.name()).matches()) {
                throw new IllegalArgumentException("Invalid object param name: " + param.name()
                        + ". It should only contain alphanumeric characters, '-' or '_', and start with a letter or '_'.");
            }
        } else if (param.name() == null || !PARAM_NAME.matcher(param.name()).matches()) {
            throw new IllegalArgumentException("Invalid param name: " + param.name()
                    + ". It should only contain alphanumeric characters, '-', '_' or '.', and start with a letter or '_'.");
        }
    }

    private static void validateObjectKey(ParamDefinition param, String key) {
        if (key == null || !OBJECT_PARAM_NAME.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid key " + key + " of object param " + param.name()
                    + ". It should only contain alphanumeric characters, '-' or '_', and start with a letter or '_'.");
        }
    }

    private static List<String> split(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
