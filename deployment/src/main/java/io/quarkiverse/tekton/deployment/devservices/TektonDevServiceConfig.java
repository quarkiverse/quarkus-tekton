package io.quarkiverse.tekton.deployment.devservices;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.tekton.devservices")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface TektonDevServiceConfig {

    /**
     * Enable the Tekton DevService.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Enable the debugging level.
     */
    @WithDefault("false")
    boolean debugEnabled();

    /**
     * If logs should be shown from the Tekton container.
     */
    @WithDefault("false")
    boolean showLogs();

    /**
     * The version of Tekton to be installed from the GitHub repository
     * and which corresponds to a tagged release expressed as such: "v0.68.0"
     */
    @WithDefault("v0.68.0")
    String version();

    /**
     * The Tekton controller namespace where Tekton stuffs are deployed
     * The default namespace is: tekton-pipelines
     */
    @WithDefault("tekton-pipelines")
    String controllerNamespace();

    /**
     * Time to wait till a resource is ready: pod, etc
     * The default value is: 180 seconds
     */
    @WithDefault("360")
    long timeOut();

    /**
     * The cluster type to be used: kind or k3
     * The default value is: kind
     */
    @WithDefault("kind")
    String clusterType();

    /**
     * The hostname of the tekton ingress route
     */
    @WithDefault("tekton.localtest.me")
    String hostName();

    /**
     * The host port to be used on the host machine to access the dashboard
     */
    @WithDefault("9097")
    String hostPort();

    /**
     * Ingress configuration
     */
    Ingress ingress();

    interface Ingress {
        /**
         * The version of the Ingress controller to be installed from the GitHub repository
         * If not specified, it will use the resources published on main branch
         * The version to be used should be specified using the tagged release: v1.12.0, etc
         */
        @WithDefault("latest")
        String version();

        /**
         * Enable to forward the ingress traffic from the container to the local host
         */
        @WithDefault("true")
        boolean portForwardEnabled();
    }

}