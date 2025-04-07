package io.quarkiverse.tekton.common.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    public static List<Param> createSingle(String name, String type, List<String> list) {
        List<Param> result = new ArrayList<>();
        result.add(new ParamBuilder()
                .withName(name)
                .withNewValue()
                .withType(type)
                .withStringVal("string".equals(type) ? list.stream().collect(Collectors.joining(" ")) : null)
                .withArrayVal("array".equals(type) ? list : new ArrayList<>())
                .endValue()
                .build());
        return result;
    }

    public static List<Param> create(List<String> list) {
        List<Param> result = new ArrayList<>();
        Iterator<String> iterator = list.iterator();
        String key = null;
        String value = null;
        while (iterator.hasNext()) {
            String s = iterator.next();
            if (key != null) {
                value = s;
                result.add(create(key, value));
                key = null;
            } else if (isValidKeyValue(s)) {
                result.add(create(s));
            } else {
                key = s;
            }
        }
        return result;
    }

    public static Param create(String name, Object value) {
        String stringVal = null;
        String[] arrayVal = value instanceof String[] ? (String[]) value : null;
        Map<String, String> objectVal = value instanceof Map ? (Map) value : null;

        if (value instanceof String) {
            // Try to split the content of the string to see if the content could be split into an array
            // using as separator a space
            String val = (String) value;
            if (val.split(" ").length > 1) {
                arrayVal = val.split(" ");
            } else {
                stringVal = val;
            }
        }

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
