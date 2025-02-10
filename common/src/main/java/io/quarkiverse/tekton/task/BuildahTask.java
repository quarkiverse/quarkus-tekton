package io.quarkiverse.tekton.task;

import java.util.Map;

import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.SecurityContext;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;
import io.fabric8.tekton.v1.Task;
import io.fabric8.tekton.v1.TaskBuilder;
import io.quarkiverse.tekton.common.utils.Resources;

public class BuildahTask {

    private static final SecurityContext RUN_AS_USER_ZERO = new SecurityContextBuilder()
            .withRunAsUser(0L)
            .build();

    private static final String BUILDAH_BUILD_SCRIPT = Resources.read("/buildah-build-script.sh");
    private static final String SBOM_SYFT_GENERATE_SCRIPT = Resources.read("/sbom-syft-generate.sh");
    private static final String INJECT_SBOM_AND_PUSH_SCRIPT = Resources.read("/inject-sbom-and-push.sh");
    private static final String PREPARE_SBOMS_SCRIPT = Resources.read("/prepare-sboms.sh");
    private static final String UPLOAD_SBOM_SCRIPT = Resources.read("/upload-sbom.sh");

    public static Task create() {
        return new TaskBuilder()
                .withApiVersion("tekton.dev/v1")
                .withKind("Task")
                .withNewMetadata()
                .withName("buildah")
                .addToAnnotations("tekton.dev/pipelines.minVersion", "0.12.1")
                .addToAnnotations("tekton.dev/tags", "image-build, appstudio, hacbs")
                .endMetadata()
                .withNewSpec()
                .withDescription(
                        """
                                    Buildah task builds source code into a container image and pushes the image into container registry using buildah tool.
                                    In addition it generates a SBOM file, injects the SBOM file into final container image and pushes the SBOM file as separate image using cosign tool.
                                    When [Java dependency rebuild](https://redhat-appstudio.github.io/docs.stonesoup.io/Documentation/main/cli/proc_enabled_java_dependencies.html) is enabled it triggers rebuilds of Java artifacts.
                                  When prefetch-dependencies task was activated it is using its artifacts to run build in hermetic environment.
                                """)
                // --- Params ---
                .addNewParam()
                .withName("IMAGE")
                .withType("string")
                .withDescription("Reference of the image buildah will produce.")
                .endParam()
                .addNewParam()
                .withName("BUILDER_IMAGE")
                .withDescription("The location of the buildah builder image.")
                .withType("string")
                .withNewDefault(
                        "quay.io/redhat-appstudio/buildah:v1.31.0@sha256:34f12c7b72ec2c28f1ded0c494b428df4791c909f1f174dd21b8ed6a57cf5ddb")
                .endParam()
                .addNewParam()
                .withName("DOCKERFILE")
                .withDescription("Path to the Dockerfile to build.")
                .withType("string")
                .withNewDefault("./Dockerfile")
                .endParam()
                .addNewParam()
                .withName("CONTEXT")
                .withDescription("Path to the directory to use as context.")
                .withType("string")
                .withNewDefault(".")
                .endParam()
                .addNewParam()
                .withName("TLSVERIFY")
                .withDescription("Verify the TLS on the registry endpoint (for push/pull to a non-TLS registry)")
                .withType("string")
                .withNewDefault("true")
                .endParam()
                .addNewParam()
                .withName("REGISTRY_AUTH_PATH")
                .withType("string")
                .withDescription("default path of the registry authentication file")
                .withNewDefault("")
                .endParam()
                .addNewParam()
                .withName("HERMETIC")
                .withDescription("Determines if build will be executed without network access.")
                .withType("string")
                .withNewDefault("false")
                .endParam()
                .addNewParam()
                .withName("PREFETCH_INPUT")
                .withDescription("In case it is not empty, the prefetched content should be made available to the build.")
                .withType("string")
                .withNewDefault("")
                .endParam()
                .addNewParam()
                .withName("IMAGE_EXPIRES_AFTER")
                .withType("string")
                .withDescription("""
                        Delete image tag after specified time. Empty means to keep the image tag.
                        Time values could be something like 1h, 2d, 3w for hours, days, and weeks, respectively.
                        """)
                .withNewDefault("")
                .endParam()
                .addNewParam()
                .withName("COMMIT_SHA")
                .withDescription("The image is built from this commit.")
                .withType("string")
                .withNewDefault("")
                .endParam()

                // --- Results ---
                .addNewResult()
                .withName("IMAGE_DIGEST")
                .withDescription("Digest of the image just built")
                .endResult()
                .addNewResult()
                .withName("IMAGE_URL")
                .withDescription("Image repository where the built image was pushed")
                .endResult()
                .addNewResult()
                .withName("BASE_IMAGES_DIGESTS")
                .withDescription("Digests of the base images used for build")
                .endResult()
                .addNewResult()
                .withName("SBOM_JAVA_COMPONENTS_COUNT")
                .withType("string")
                .withDescription("The counting of Java components by publisher in JSON format")
                .endResult()
                .addNewResult()
                .withName("JAVA_COMMUNITY_DEPENDENCIES")
                .withDescription("The Java dependencies that came from community sources such as Maven central.")
                .endResult()

                // --- Step Template ---
                .withNewStepTemplate()
                .addNewEnv()
                .withName("BUILDAH_FORMAT")
                .withValue("oci")
                .endEnv()
                .addNewEnv()
                .withName("STORAGE_DRIVER")
                .withValue("vfs")
                .endEnv()
                .addNewEnv()
                .withName("HERMETIC")
                .withValue("$(params.HERMETIC)")
                .endEnv()
                .addNewEnv()
                .withName("PREFETCH_INPUT")
                .withValue("$(params.PREFETCH_INPUT)")
                .endEnv()
                .addNewEnv()
                .withName("CONTEXT")
                .withValue("$(params.CONTEXT)")
                .endEnv()
                .addNewEnv()
                .withName("DOCKERFILE")
                .withValue("$(params.DOCKERFILE)")
                .endEnv()
                .addNewEnv()
                .withName("REGISTRY_AUTH_FILE")
                .withValue("$(params.REGISTRY_AUTH_PATH)/config.json")
                .endEnv()
                .addNewEnv()
                .withName("DOCKER_CONFIG")
                .withValue("$(params.REGISTRY_AUTH_PATH)")
                .endEnv()
                .addNewEnv()
                .withName("IMAGE")
                .withValue("$(params.IMAGE)")
                .endEnv()
                .addNewEnv()
                .withName("TLSVERIFY")
                .withValue("$(params.TLSVERIFY)")
                .endEnv()
                .addNewEnv()
                .withName("IMAGE_EXPIRES_AFTER")
                .withValue("$(params.IMAGE_EXPIRES_AFTER)")
                .endEnv()
                .endStepTemplate()

                // --- Steps ---
                .addNewStep()
                .withName("build")
                .withImage("$(params.BUILDER_IMAGE)")
                .addNewEnv()
                .withName("COMMIT_SHA")
                .withValue("$(params.COMMIT_SHA)")
                .endEnv()
                .withScript(BUILDAH_BUILD_SCRIPT)
                .withSecurityContext(new SecurityContextBuilder()
                        .withNewCapabilities()
                        .addToAdd("SETFCAP")
                        .endCapabilities()
                        .build())
                .addNewVolumeMount()
                .withName("varlibcontainers")
                .withMountPath("/var/lib/containers")
                .endVolumeMount()
                .withWorkingDir("$(workspaces.source.path)")
                .withNewComputeResources()
                .withRequests(
                        Map.of("memory", Quantity.parse("512Mi"),
                                "cpu", Quantity.parse("250m")))
                .withLimits(Map.of("memory", Quantity.parse("4Gi")))
                .endComputeResources()
                .endStep()

                .addNewStep()
                .withName("sbom-syft-generate")
                .withImage(
                        "registry.access.redhat.com/rh-syft-tech-preview/syft-rhel9:1.19.0@sha256:070ecb89de5104bb64fbf399a991a975e7d4d7e0cea0f7beb1e591b5591991c8")
                .withScript(SBOM_SYFT_GENERATE_SCRIPT)
                .addNewVolumeMount()
                .withName("varlibcontainers")
                .withMountPath("/var/lib/containers")
                .endVolumeMount()
                .withNewComputeResources()
                .withRequests(
                        Map.of("memory", Quantity.parse("512Mi"),
                                "cpu", Quantity.parse("250m")))
                .withLimits(Map.of("memory", Quantity.parse("4Gi")))
                .endComputeResources()
                .endStep()

                .addNewStep()
                .withName("prpare-sboms")
                .withImage(
                        "quay.io/konflux-ci/sbom-utility-scripts@sha256:a0ed2421c04a07e0c1f0f026cff3d6778feb9e2abe36bc40c40629711daff930")
                .withScript(PREPARE_SBOMS_SCRIPT)
                .addNewVolumeMount()
                .withName("varlibcontainers")
                .withMountPath("/var/lib/containers")
                .endVolumeMount()
                .withNewComputeResources()
                .withRequests(
                        Map.of("memory", Quantity.parse("512Mi"),
                                "cpu", Quantity.parse("250m")))
                .withLimits(Map.of("memory", Quantity.parse("4Gi")))
                .endComputeResources()
                .endStep()

                .addNewStep()
                .withName("inject-sbom-and-push")
                .withImage(
                        "quay.io/konflux-ci/buildah-task:latest@sha256:ab0ba3b70f99faa74d2dd737422a965197af4922dec0109113bc535a94db0dfd")
                .withScript(INJECT_SBOM_AND_PUSH_SCRIPT)
                .withSecurityContext(new SecurityContextBuilder(RUN_AS_USER_ZERO)
                        .withRunAsUser(0L)
                        .withNewCapabilities()
                        .addToAdd("SETFCAP")
                        .endCapabilities()
                        .build())
                .addNewVolumeMount()
                .withName("varlibcontainers")
                .withMountPath("/var/lib/containers")
                .endVolumeMount()
                .withWorkingDir("$(workspaces.source.path)")
                .endStep()

                .addNewStep()
                .withName("upload-sbom")
                .withImage(
                        "quay.io/redhat-appstudio/cosign:v2.1.1@sha256:c883d6f8d39148f2cea71bff4622d196d89df3e510f36c140c097b932f0dd5d5")
                .withScript(UPLOAD_SBOM_SCRIPT)
                .withWorkingDir("/var/workdir")
                .addNewVolumeMount()
                .withName("trusted-ca")
                .withMountPath("/mnt/trusted-ca")
                .endVolumeMount()
                .withNewComputeResources()
                .withRequests(Map.of("memory", Quantity.parse("512Mi"), "cpu", Quantity.parse("256m")))
                .withLimits(Map.of("memory", Quantity.parse("4Gi")))
                .endComputeResources()
                .endStep()

                // --- Volumes ---
                .addNewVolume()
                .withName("varlibcontainers")
                .withNewEmptyDir()
                .endEmptyDir()
                .endVolume()

                // --- Workspaces ---
                .addNewWorkspace()
                .withName("source")
                .withDescription("Workspace containing the source code to build.")
                .endWorkspace()
                .addNewWorkspace()
                .withName("dockerconfig")
                .withDescription("Workspace containing the registry credentials")
                .endWorkspace()
                .endSpec()
                .build();
    }
}
