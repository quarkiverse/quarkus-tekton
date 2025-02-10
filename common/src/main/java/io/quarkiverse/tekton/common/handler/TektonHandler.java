package io.quarkiverse.tekton.common.handler;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.quarkiverse.tekton.spi.GeneratedTektonResourceBuildItem;
import io.quarkus.builder.BuildResult;

public class TektonHandler implements BiConsumer<Object, BuildResult> {

    @Override
    public void accept(Object context, BuildResult buildResult) {
        List<GeneratedTektonResourceBuildItem> tektonResourceBuildItems = buildResult
                .consumeMulti(GeneratedTektonResourceBuildItem.class);
        List<HasMetadata> tektonResources = tektonResourceBuildItems.stream().flatMap(t -> t.getResources().stream())
                .collect(Collectors.toList());
        if (context instanceof TektonResourcesProcessor p) {
            p.process(tektonResources);
        }
    }
}
