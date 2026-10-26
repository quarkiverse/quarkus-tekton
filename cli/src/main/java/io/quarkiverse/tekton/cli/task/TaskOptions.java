package io.quarkiverse.tekton.cli.task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.quarkiverse.tekton.task.ParamDefinition;
import io.quarkiverse.tekton.task.WorkspaceDefinition;

/**
 * Parses the options of {@code quarkus tekton task generate}.
 * The params and workspaces are sorted by name, as the ones configured using {@code quarkus.tekton.task.<name>.*}.
 */
public class TaskOptions {

    /**
     * Parses a {@code KEY=VALUE} option.
     */
    static Map.Entry<String, String> parseKeyValue(String option, String value) {
        int index = value.indexOf('=');
        if (index <= 0) {
            throw new IllegalArgumentException("Invalid " + option + " value: " + value + ". Expected KEY=VALUE.");
        }
        return Map.entry(value.substring(0, index).trim(), value.substring(index + 1));
    }

    /**
     * Parses repeated {@code KEY=VALUE} options, keeping their order.
     */
    static Map<String, String> parseKeyValues(String option, List<String> values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String value : values) {
            Map.Entry<String, String> entry = parseKeyValue(option, value);
            if (result.put(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalArgumentException(option + " " + entry.getKey() + " is given twice.");
            }
        }
        return result;
    }

    /**
     * Parses a {@code name[:type][=default]} param (e.g. {@code who=world}, {@code labels:object}).
     */
    static ParamDefinition parseParam(String value) {
        int index = value.indexOf('=');
        String spec = index < 0 ? value : value.substring(0, index);
        Optional<String> defaultValue = index < 0 ? Optional.empty() : Optional.of(value.substring(index + 1));

        String[] parts = spec.split(":", -1);
        if (parts.length > 2 || parts[0].isBlank() || (parts.length == 2 && parts[1].isBlank())) {
            throw new IllegalArgumentException("Invalid --param value: " + value
                    + ". Expected name[:type][=default] (e.g. who=world, files:array=a,b or labels:object).");
        }
        // an empty value is a missing value for SmallRye Config
        if (defaultValue.filter(String::isEmpty).isPresent()) {
            throw new IllegalArgumentException("Invalid --param value: " + value
                    + ". The default value cannot be empty, remove the '=' to declare a param without default value.");
        }
        return new ParamDefinition(parts[0].trim(), parts.length == 2 ? parts[1].trim() : null, defaultValue,
                Optional.empty());
    }

    /**
     * Parses the params, adding them their description ({@code name=description}) and properties
     * ({@code name=key1,key2}).
     */
    static List<ParamDefinition> parseParams(List<String> params, List<String> descriptions, List<String> properties) {
        Map<String, ParamDefinition> result = new LinkedHashMap<>();
        for (String value : params) {
            ParamDefinition param = parseParam(value);
            if (result.put(param.name(), param) != null) {
                throw new IllegalArgumentException("Param " + param.name() + " is declared twice.");
            }
        }

        parseKeyValues("--param-description", descriptions).forEach((name, description) -> {
            ParamDefinition param = declaredParam(result, "--param-description", name);
            result.put(name, new ParamDefinition(name, param.type(), param.defaultValue(), Optional.of(description),
                    param.properties()));
        });

        parseKeyValues("--param-properties", properties).forEach((name, keys) -> {
            ParamDefinition param = declaredParam(result, "--param-properties", name);
            List<String> list = Arrays.stream(keys.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (list.isEmpty()) {
                throw new IllegalArgumentException("Invalid --param-properties value: " + name + "=" + keys
                        + ". Expected name=key1,key2.");
            }
            result.put(name, new ParamDefinition(name, param.type(), param.defaultValue(), param.description(), list));
        });

        List<ParamDefinition> sorted = new ArrayList<>(result.values());
        sorted.sort(Comparator.comparing(ParamDefinition::name));
        return sorted;
    }

    /**
     * Parses a {@code name[:mountPath][:optional]} workspace (e.g. {@code source}, {@code source:/workspace/src},
     * {@code source::optional}).
     */
    static WorkspaceDefinition parseWorkspace(String value) {
        String[] parts = value.split(":", -1);
        String name = parts[0].trim();
        String mountPath = null;
        boolean optional = false;
        if (parts.length == 2) {
            if ("optional".equals(parts[1].trim())) {
                optional = true;
            } else {
                mountPath = parts[1].trim();
            }
        } else if (parts.length == 3) {
            mountPath = parts[1].trim();
            String flag = parts[2].trim();
            if (!flag.isEmpty() && !"optional".equals(flag)) {
                throw invalidWorkspace(value);
            }
            optional = !flag.isEmpty();
        }
        if (name.isEmpty() || parts.length > 3) {
            throw invalidWorkspace(value);
        }
        return new WorkspaceDefinition(name, Optional.ofNullable(mountPath).filter(s -> !s.isEmpty()), optional,
                Optional.empty());
    }

    static List<WorkspaceDefinition> parseWorkspaces(List<String> workspaces) {
        List<WorkspaceDefinition> result = new ArrayList<>(workspaces.stream().map(TaskOptions::parseWorkspace).toList());
        result.sort(Comparator.comparing(WorkspaceDefinition::name));
        return result;
    }

    private static ParamDefinition declaredParam(Map<String, ParamDefinition> params, String option, String name) {
        ParamDefinition param = params.get(name);
        if (param == null) {
            throw new IllegalArgumentException(option + " " + name + ": param " + name
                    + " is not declared using --param. Declared params: " + String.join(", ", params.keySet()) + ".");
        }
        return param;
    }

    private static IllegalArgumentException invalidWorkspace(String value) {
        return new IllegalArgumentException("Invalid --workspace value: " + value
                + ". Expected name[:mountPath][:optional] (e.g. source, source:/workspace/src or source::optional).");
    }
}
