package io.quarkiverse.tekton.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

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
     * Custom tasks, keyed by task name. A custom task having the same name as a provided task (e.g. `maven`) replaces it.
     */
    @WithName("task")
    Map<String, CustomTaskConfig> tasks();

    interface CustomTaskConfig {
        /**
         * Description of the task.
         */
        Optional<String> description();

        /**
         * Image of the step running the task.
         */
        String image();

        /**
         * Script executed by the step. Cannot be used with `command`.
         */
        Optional<String> script();

        /**
         * Command executed by the step (e.g. `sh,-c`). Cannot be used with `script`.
         */
        Optional<List<String>> command();

        /**
         * Arguments passed to the command or script of the step.
         */
        Optional<List<String>> args();

        /**
         * Working directory of the step (e.g. `$(workspaces.project-dir.path)`).
         */
        Optional<String> workingDir();

        /**
         * Environment variables of the step.
         */
        Map<String, String> env();

        /**
         * Params of the task, keyed by param name.
         */
        Map<String, ParamConfig> param();

        /**
         * Workspaces of the task, keyed by workspace name.
         */
        Map<String, WorkspaceConfig> workspace();
    }

    interface ParamConfig {
        /**
         * Type of the param: `string`, `array` or `object`.
         */
        @WithDefault("string")
        String type();

        /**
         * Default value of the param. A comma-separated list for an `array` param, comma-separated `key=value` pairs for
         * an `object` param.
         */
        @WithName("default")
        Optional<String> defaultValue();

        /**
         * Description of the param.
         */
        Optional<String> description();

        /**
         * Properties (keys) of an `object` param. When not set, the keys of the default value are used.
         */
        Optional<List<String>> properties();
    }

    interface WorkspaceConfig {
        /**
         * Path where the workspace is mounted (default: `/workspace/<name>`).
         */
        Optional<String> mountPath();

        /**
         * Whether the workspace is optional.
         */
        @WithDefault("false")
        boolean optional();

        /**
         * Description of the workspace.
         */
        Optional<String> description();
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
