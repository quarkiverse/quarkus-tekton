package io.quarkiverse.tekton.cli.common;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder;
import io.fabric8.tekton.v1.WorkspaceBinding;
import io.fabric8.tekton.v1.WorkspaceBindingBuilder;

public class WorkspaceBindings {

    /**
     * A non-generic Function that maps String.
     * This is mostly used for use in var-args.
     */
    @FunctionalInterface
    public interface Mapper {
        String apply(String name);
    }

    private static Map<String, PersistentVolumeClaim> pvcClaims = Clients.kubernetes().persistentVolumeClaims().list()
            .getItems().stream()
            .collect(Collectors.toMap(p -> p.getMetadata().getName(), Function.identity()));

    private static Map<String, ConfigMap> configMaps = Clients.kubernetes().configMaps().list()
            .getItems().stream()
            .collect(Collectors.toMap(c -> c.getMetadata().getName(), Function.identity()));

    private static Map<String, Secret> secrets = Clients.kubernetes().secrets().list()
            .getItems().stream()
            .collect(Collectors.toMap(s -> s.getMetadata().getName(), Function.identity()));

    public static Optional<WorkspaceBinding> forEmpty(String name) {
        return Optional.of(
                new WorkspaceBindingBuilder().withName(name).withEmptyDir(new EmptyDirVolumeSourceBuilder().build()).build());
    }

    public static Optional<WorkspaceBinding> forName(String name) {
        if (name.endsWith("-dir")) {
            return forPvc(name,
                    n -> n.replaceAll("-dir$", ""),
                    n -> n.replaceAll("-dir$", "-pvc"),
                    n -> n.replaceAll("-pvc$", ""))
                    .or(() -> forEmpty(name));
        }
        if (name.endsWith("-pvc")) {
            return forPvc(name,
                    n -> n.replaceAll("-dir$", ""),
                    n -> n.replaceAll("-dir$", "-pvc"),
                    n -> n.replaceAll("-pvc$", ""));
        }

        if (name.endsWith("-configmap") || name.endsWith("-cm") || name.endsWith("-cfg")) {
            return forConfigMap(name,
                    n -> n.replaceAll("-configmap$", ""),
                    n -> n.replaceAll("-configmap$", "-cm"),
                    n -> n.replaceAll("-configmap$", "-cfg"),
                    n -> n.replaceAll("-cm$", ""),
                    n -> n.replaceAll("-cm$", "-cfg"),
                    n -> n.replaceAll("-cfg$", ""),
                    n -> n.replaceAll("-cfg$", "-cm"));
        }

        if (name.endsWith("-secret") || name.endsWith("-scfg")) {
            return forSecret(name,
                    n -> n.replaceAll("-secret$", ""),
                    n -> n.replaceAll("-scfg$", ""));
        }

        // No conventions detected, check for exact matches
        return forPvc(name).or(() -> forConfigMap(name)).or(() -> forSecret(name)).or(() -> forEmpty(name));
    }

    public static Optional<WorkspaceBinding> forPvc(String name, Mapper... mappers) {
        if (pvcClaims.containsKey(name)) {
            return Optional.of(new WorkspaceBindingBuilder().withName(name).withNewPersistentVolumeClaim(name, false).build());
        }

        for (Mapper mapper : mappers) {
            String newName = mapper.apply(name);
            if (pvcClaims.containsKey(newName)) {
                return forPvc(newName).map(b -> {
                    b.setName(name);
                    return b;
                });
            }
        }
        return Optional.empty();
    }

    public static Optional<WorkspaceBinding> forConfigMap(String name, Mapper... mappers) {
        if (configMaps.containsKey(name)) {
            return Optional.of(new WorkspaceBindingBuilder().withName(name)
                    .withConfigMap(new ConfigMapVolumeSourceBuilder().withName(name).build()).build());
        }
        for (Mapper mapper : mappers) {
            String newName = mapper.apply(name);
            if (configMaps.containsKey(newName)) {
                return forConfigMap(newName).map(b -> {
                    b.setName(name);
                    return b;
                });
            }
        }
        return Optional.empty();
    }

    public static Optional<WorkspaceBinding> forSecret(String name, Mapper... mappers) {
        if (secrets.containsKey(name)) {
            return Optional.of(new WorkspaceBindingBuilder().withName(name)
                    .withSecret(new SecretVolumeSourceBuilder().withSecretName(name).build()).build());
        }
        for (Mapper mapper : mappers) {
            String newName = mapper.apply(name);
            if (configMaps.containsKey(newName)) {
                return forConfigMap(newName).map(b -> {
                    b.setName(name);
                    return b;
                });
            }
        }
        return Optional.empty();
    }
}
