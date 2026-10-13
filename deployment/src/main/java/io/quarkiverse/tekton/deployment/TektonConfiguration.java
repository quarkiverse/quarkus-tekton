package io.quarkiverse.tekton.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.tekton")
@ConfigRoot(phase = BUILD_TIME)
public interface TektonConfiguration {

    /**
     * PipelineRun configuration
     */
    PipelineRun pipelinerun();

    interface PipelineRun {
        /**
         * Whether to enable the tekton generation at build time of the PipelineRun.
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * User's arguments to customize the pipeline
         */
        Map<String, String> params();

        /**
         * Name of the Secret, expected to exist on the cluster, containing the registry auth file (config.json)
         * which is bound to the `dockerconfig-secret` workspace of the PipelineRun.
         */
        @WithDefault("dockerconfig-secret")
        String registryAuthSecret();

        /**
         * Name of the ConfigMap, expected to exist on the cluster, containing the CA certificate(s) used to verify the
         * TLS certificate of the git server and the image registry (e.g. a self-signed certificate). When set, the ConfigMap
         * is bound to the `ssl-ca-directory` workspace of the PipelineRun. The name of the certificate file within the
         * ConfigMap is defined using the pipeline parameter `crtFileName` (default: `ca-bundle.crt`).
         */
        Optional<String> sslCaConfigMap();
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
