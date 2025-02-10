package io.quarkiverse.tekton.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.tekton")
public interface TektonConfiguration {

    /**
     * Generation configuration
     **/
    Generation generation();

    interface Generation {
        /**
         * Whether to enable tekton generation at build time.
         */
        @WithDefault("true")
        boolean enabled();

    }
}
