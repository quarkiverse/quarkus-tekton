package io.quarkiverse.tekton.common.utils;

import java.util.ArrayList;
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

    public static <T> List<Param> create(T input) {
        Map<String, String> map;
        /**
         * We got a quarkus tekton params property where params are defined as:
         *
         * -Dquarkus.tekton.pipelinerun.params.url=gitea.cnoe.localtest.me:8443/quarkus/my-quarkus-app-job
         * -Dquarkus.tekton.pipelinerun.params.sslVerify=false
         * -Dquarkus.tekton.pipelinerun.params.output-image=gitea.cnoe.localtest.me:8443/quarkus/my-quarkus-app-job
         * -Dquarkus.tekton.pipelinerun.params.mavenGoals="-Dquarkus.container-image.build=false
         * -Dquarkus.container-image.push=false package"
         */
        if (input instanceof Map<?, ?>) {
            map = (Map<String, String>) input;
            /**
             * We got a List<String> from the Quarkus tekton CLI
             *
             * quarkus pipeline exec build-test-push \
             * sslVerify=false \
             * output-image=gitea.cnoe.localtest.me:8443/quarkus/my-quarkus-app-job \
             * url=https://gitea.cnoe.localtest.me:8443/quarkus/my-quarkus-app-job.git \
             * mavenGoals="-Dquarkus.container-image.build=false -Dquarkus.container-image.push=false
             * -Dquarkus.container-image.image=gitea.cnoe.localtest.me:8443/quarkus/my-quarkus-app-job package"
             *
             * It is then needed to convert the List<String> of arguments into a Map<String,String> where the key is equal to
             * the left part of key=val
             */
        } else if (input instanceof List<?>) {
            map = ((List<String>) input).stream()
                    .map(s -> s.split("=", 2)) // Split each string into at most two parts
                    .filter(parts -> parts.length == 2) // Ensure we have both key and value
                    .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1]));
        } else {
            throw new IllegalArgumentException("Unsupported input type");
        }

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
