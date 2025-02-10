package io.quarkiverse.tekton.pvc;

import java.util.Map;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;

public class ProjectWorkspacePvc {

    public static PersistentVolumeClaim create(String name) {
        return new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withName(name + "-project-pvc")
                .endMetadata()
                .withNewSpec()
                .withAccessModes("ReadWriteOnce")
                .withNewResources()
                .withRequests(Map.of("storage", new Quantity("2Gi")))
                .endResources()
                .withVolumeMode("Filesystem")
                .endSpec()
                .build();
    }
}
