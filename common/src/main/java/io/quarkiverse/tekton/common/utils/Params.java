package io.quarkiverse.tekton.common.utils;

import java.util.ArrayList;
import java.util.Iterator;
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

    public static <T> List<Param> create(T input) {
        List<Param> result = new ArrayList<>();
        /**
         * We got a quarkus tekton params property where params are defined as:
         *
         * -Dquarkus.tekton.pipelinerun.params.url=gitea.cnoe.localtest.me:8443/quarkus/my-quarkus-app-job
         * -Dquarkus.tekton.pipelinerun.params.sslVerify=false
         * -Dquarkus.tekton.pipelinerun.params.output-image=gitea.cnoe.localtest.me:8443/quarkus/my-quarkus-app-job
         * -Dquarkus.tekton.pipelinerun.params.mavenGoals="-Dquarkus.container-image.build=false
         * -Dquarkus.container-image.push=false package"
         */
        if (input instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                result.add(create(String.valueOf(k), v));
            });

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
            Iterator<String> iterator = ((List<String>) input).iterator();
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
        } else {
            throw new IllegalArgumentException("Unsupported input type");
        }

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
