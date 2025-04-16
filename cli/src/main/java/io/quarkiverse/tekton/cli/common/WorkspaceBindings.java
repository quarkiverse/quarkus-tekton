package io.quarkiverse.tekton.cli.common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClientException;
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

    private static final Logger log = LoggerFactory.getLogger(WorkspaceBindings.class);

    private static final Map<String, PersistentVolumeClaim> PVC_CLAIMS = new HashMap<>();
    private static final Map<String, ConfigMap> CONFIG_MAPS = new HashMap<>();
    private static final Map<String, Secret> SECRETS = new HashMap<>();

    public static void readClusterBindingResources() {
        readClusterPvcs();
        readClusterConfigMaps();
        readClusterSecrets();
    }

    public static void readClusterPvcs() {
        PVC_CLAIMS.putAll(Clients.kubernetes().persistentVolumeClaims().list()
                .getItems().stream()
                .collect(Collectors.toMap(p -> p.getMetadata().getName(), Function.identity())));
    }

    public static void readClusterConfigMaps() {
        CONFIG_MAPS.putAll(Clients.kubernetes().configMaps().list()
                .getItems().stream()
                .collect(Collectors.toMap(
                        // Use a composite key to avoid java.util.stream.Collectors.duplicateKeyException
                        c -> c.getMetadata().getNamespace() + "/" + c.getMetadata().getName(),
                        Function.identity())));
    }

    public static void readClusterSecrets() {
        SECRETS.putAll(Clients.kubernetes().secrets().list()
                .getItems().stream()
                .collect(Collectors.toMap(
                        // Use a composite key to avoid java.util.stream.Collectors.duplicateKeyException
                        s -> s.getMetadata().getNamespace() + "/" + s.getMetadata().getName(),
                        Function.identity())));
    }

    public static void readBindingResources(List<HasMetadata> resources) {
        for (HasMetadata resource : resources) {
            if (resource instanceof PersistentVolumeClaim pvc) {
                PVC_CLAIMS.put(pvc.getMetadata().getName(), pvc);
            }
            if (resource instanceof ConfigMap cfg) {
                CONFIG_MAPS.put(cfg.getMetadata().getName(), cfg);
            }
            if (resource instanceof Secret secret) {
                SECRETS.put(secret.getMetadata().getName(), secret);
            }
        }
    }

    public static Optional<WorkspaceBinding> forEmpty(String applicationName, String workspaceName) {
        return Optional.of(new WorkspaceBindingBuilder().withName(workspaceName)
                .withEmptyDir(new EmptyDirVolumeSourceBuilder().build()).build());
    }

    public static Optional<WorkspaceBinding> forName(String applicationName, String workspaceName) {
        if (workspaceName.endsWith("-dir")) {
            return forPvc(applicationName, workspaceName,
                    n -> n.replaceAll("-dir$", ""),
                    n -> n.replaceAll("-dir$", "-pvc"),
                    n -> n.replaceAll("-pvc$", ""));
        }
        if (workspaceName.endsWith("-pvc")) {
            return forPvc(applicationName, workspaceName,
                    n -> n.replaceAll("-dir$", ""),
                    n -> n.replaceAll("-dir$", "-pvc"),
                    n -> n.replaceAll("-pvc$", ""));
        }

        if (workspaceName.endsWith("-configmap") || workspaceName.endsWith("-cm") || workspaceName.endsWith("-cfg")) {
            return forConfigMap(applicationName, workspaceName,
                    n -> n.replaceAll("-configmap$", ""),
                    n -> n.replaceAll("-configmap$", "-cm"),
                    n -> n.replaceAll("-configmap$", "-cfg"),
                    n -> n.replaceAll("-cm$", ""),
                    n -> n.replaceAll("-cm$", "-cfg"),
                    n -> n.replaceAll("-cfg$", ""),
                    n -> n.replaceAll("-cfg$", "-cm"));
        }

        if (workspaceName.endsWith("-secret") || workspaceName.endsWith("-scfg")) {
            return forSecret(applicationName, workspaceName,
                    n -> n.replaceAll("-secret$", ""),
                    n -> n.replaceAll("-scfg$", ""));
        }

        // No conventions detected, check for exact matches
        return forPvc(applicationName, workspaceName)
                .or(() -> forConfigMap(applicationName, workspaceName))
                .or(() -> forSecret(applicationName, workspaceName));
    }

    public static Optional<WorkspaceBinding> forPvc(String applicationName, String workspaceName, Mapper... mappers) {
        return forPvc(applicationName, workspaceName, true, mappers);
    }

    public static Optional<WorkspaceBinding> forPvc(String applicationName, String workspaceName, boolean reReadClusterPvcs,
            Mapper... mappers) {
        if (reReadClusterPvcs) {
            try {
                readClusterPvcs();
            } catch (KubernetesClientException e) {
                log.warn(
                        "Access to the kubernetes cluster to get the Pvc for the workspaceName: {} failed. Is the cluster available ?",
                        workspaceName);
            }
        }
        String name = applicationName + "-" + workspaceName;
        if (PVC_CLAIMS.containsKey(name)) {
            return Optional.of(
                    new WorkspaceBindingBuilder().withName(workspaceName).withNewPersistentVolumeClaim(name, false).build());
        }

        for (Mapper mapper : mappers) {
            String newWorkspaceName = mapper.apply(workspaceName);
            String newName = applicationName + "-" + newWorkspaceName;
            if (PVC_CLAIMS.containsKey(newName)) {
                return forPvc(applicationName, newWorkspaceName, false).map(b -> {
                    b.setName(workspaceName);
                    return b;
                });
            }
        }
        return Optional.empty();
    }

    public static Optional<WorkspaceBinding> forConfigMap(String applicationName, String workspaceName, Mapper... mappers) {
        return forConfigMap(applicationName, workspaceName, true, mappers);
    }

    public static Optional<WorkspaceBinding> forConfigMap(String applicationName, String workspaceName,
            boolean reReadClusterConfigMaps, Mapper... mappers) {
        if (reReadClusterConfigMaps) {
            try {
                readClusterConfigMaps();
            } catch (KubernetesClientException e) {
                log.warn(
                        "Access to the kubernetes cluster to get the configMap for the workspaceName: {} failed. Is the cluster available ?",
                        workspaceName);
            }
        }
        String name = applicationName + "-" + workspaceName;
        if (CONFIG_MAPS.containsKey(name)) {
            return Optional.of(new WorkspaceBindingBuilder().withName(workspaceName)
                    .withConfigMap(new ConfigMapVolumeSourceBuilder().withName(name).build()).build());
        }
        for (Mapper mapper : mappers) {
            String newWorkspaceName = mapper.apply(workspaceName);
            String newName = applicationName + "-" + newWorkspaceName;
            if (CONFIG_MAPS.containsKey(newName)) {
                return forConfigMap(applicationName, newWorkspaceName, false).map(b -> {
                    b.setName(workspaceName);
                    return b;
                });
            }
        }
        return Optional.empty();
    }

    public static Optional<WorkspaceBinding> forSecret(String applicationName, String workspaceName, Mapper... mappers) {
        return forSecret(applicationName, workspaceName, true, mappers);
    }

    public static Optional<WorkspaceBinding> forSecret(String applicationName, String workspaceName,
            boolean reReadClusterSecrets, Mapper... mappers) {
        if (reReadClusterSecrets) {
            try {
                readClusterSecrets();
            } catch (KubernetesClientException e) {
                log.warn(
                        "Access to the kubernetes cluster to get the Secret for the workspaceName: {} failed. Is the cluster available ?",
                        workspaceName);
            }
        }
        String name = applicationName + "-" + workspaceName;
        if (SECRETS.containsKey(name)) {
            return Optional.of(new WorkspaceBindingBuilder().withName(workspaceName)
                    .withSecret(new SecretVolumeSourceBuilder().withSecretName(name).build()).build());
        }

        for (Mapper mapper : mappers) {
            String newWorkspaceName = mapper.apply(workspaceName);
            String newName = applicationName + "-" + newWorkspaceName;
            if (SECRETS.containsKey(newName)) {
                return forSecret(applicationName, newWorkspaceName, false).map(b -> {
                    b.setName(workspaceName);
                    return b;
                });
            }
        }

        return Optional.empty();
    }

    public static boolean createIfNeeded(WorkspaceBinding binding) {
        Optional<PersistentVolumeClaim> pvc = Optional.ofNullable(binding.getPersistentVolumeClaim())
                .map(p -> p.getClaimName())
                .filter(k -> PVC_CLAIMS.containsKey(k))
                .map(k -> PVC_CLAIMS.get(k));

        pvc.ifPresent(r -> {
            if (Clients.kubernetes().resource(r).get() == null) {
                Clients.kubernetes().resource(r).create();
            }
        });

        Optional<ConfigMap> configMap = Optional.ofNullable(binding.getConfigMap())
                .map(p -> p.getName())
                .filter(k -> CONFIG_MAPS.containsKey(k))
                .map(k -> CONFIG_MAPS.get(k));

        configMap.ifPresent(r -> {
            if (Clients.kubernetes().resource(r).get() == null) {
                Clients.kubernetes().resource(r).create();
            }
        });

        Optional<Secret> secret = Optional.ofNullable(binding.getSecret()).map(p -> p.getSecretName())
                .filter(k -> SECRETS.containsKey(k))
                .map(k -> SECRETS.get(k));

        configMap.ifPresent(r -> {
            if (Clients.kubernetes().resource(r).get() == null) {
                Clients.kubernetes().resource(r).create();
            }
        });

        return false;
    }
}
