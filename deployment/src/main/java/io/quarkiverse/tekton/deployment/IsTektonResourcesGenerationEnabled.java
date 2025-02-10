package io.quarkiverse.tekton.deployment;

import java.util.function.BooleanSupplier;

public class IsTektonResourcesGenerationEnabled implements BooleanSupplier {

    TektonConfiguration config;

    @Override
    public boolean getAsBoolean() {
        return config.generation().enabled();
    }
}
