package io.quarkiverse.tekton.common.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.fabric8.tekton.v1.Param;
import io.fabric8.tekton.v1.ParamBuilder;

public final class Params {

    private Params() {
        // Utility class
    }

    public static boolean isValidKeyValue(String s) {
        if (s == null) {
            return false;
        }
        if (s.contains("=") || s.contains(" ")) {
            return true;
        } else
            return false;
    }

    public static List<Param> create(Map<String, String> map) {
        List<Param> result = new ArrayList<>();
        map.forEach((k, v) -> {
            result.add(create(k, v));
        });
        return result;
    }

    public static Param create(String name, Object value) {
        String stringVal = value instanceof String ? (String) value : null;
        String[] arrayVal = value instanceof String[] ? (String[]) value : null;
        Map<String, String> objectVal = value instanceof Map ? (Map) value : null;

        return new ParamBuilder()
                .withName(name)
                .withNewValue()
                .withStringVal(stringVal)
                .withArrayVal(arrayVal)
                .withObjectVal(objectVal)
                .endValue()
                .build();
    }

    public static Param create(String s) {
        return create(getKey(s), getValue(s));
    }

    private static String getKey(String s) {
        String key = s.replaceFirst("^[\\-]+", "");
        if (key.contains("=")) {
            key = key.substring(0, key.indexOf("="));
        } else if (key.contains(" ")) {
            key = key.substring(0, key.indexOf(" "));
        }
        return key;
    }

    private static String getValue(String s) {
        String value = s;
        if (value.contains("=")) {
            value = value.substring(value.indexOf("=") + 1);
        } else if (value.contains(" ")) {
            value = value.substring(value.indexOf(" ") + 1);
        }
        return value;
    }
}
