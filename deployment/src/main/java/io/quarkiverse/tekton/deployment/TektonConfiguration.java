package io.quarkiverse.tekton.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.tekton")
@ConfigRoot(phase = BUILD_TIME)
public interface TektonConfiguration {

    /**
     * PipelineRun arguments to be passed as parameters
     */
    PipelineRun pipelinerun();

    interface PipelineRun {
        /**
         * User's arguments to customize the pipeline
         */
        Map<String, String> params();
    }

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
