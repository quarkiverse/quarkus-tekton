package io.quarkiverse.tekton.spi;

import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.quarkus.builder.item.MultiBuildItem;

public final class GeneratedTektonResourceBuildItem extends MultiBuildItem {

    private final List<? extends HasMetadata> resources;

    public GeneratedTektonResourceBuildItem(List<? extends HasMetadata> resources) {
        this.resources = resources;
    }

    public List<? extends HasMetadata> getResources() {
        return resources;
    }
}
