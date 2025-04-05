package io.quarkiverse.tekton.cli.common;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.quarkiverse.tekton.common.handler.TektonHandler;
import io.quarkiverse.tekton.common.handler.TektonResourcesProcessor;
import io.quarkiverse.tekton.common.utils.Projects;
import io.quarkiverse.tekton.common.utils.Resources;
import io.quarkiverse.tekton.common.utils.Serialization;
import io.quarkiverse.tekton.spi.GeneratedTektonResourceBuildItem;
import io.quarkus.bootstrap.BootstrapAppModelFactory;
import io.quarkus.bootstrap.BootstrapException;
import io.quarkus.bootstrap.app.AugmentAction;
import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.bootstrap.app.QuarkusBootstrap;
import io.quarkus.deployment.builditem.GeneratedFileSystemResourceBuildItem;
import io.quarkus.devtools.project.BuildTool;
import io.quarkus.devtools.project.QuarkusProjectHelper;
import io.quarkus.maven.dependency.ArtifactDependency;
import io.quarkus.maven.dependency.Dependency;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

public abstract class GenerationBaseCommand implements Callable<Integer> {

    private static final ArtifactDependency QUARKUS_TEKTON = new ArtifactDependency("io.quarkiverse.tekton", "quarkus-tekton",
            null, "jar", GenerationBaseCommand.getVersion());

    @Option(names = { "--namespace" }, description = "The target namespace (where the Custom Resources will be installed)")
    protected Optional<String> namespace = Optional.empty();

    @Option(names = {
            "--tekton-output-dir" }, description = "The target directory where tekton resources are meant to be generated.")
    protected Optional<Path> tektonOutpuDir = Optional.empty();

    public abstract void process(List<HasMetadata> resources);

    public String[] getRequiredBuildItems() {
        return new String[] {
                GeneratedFileSystemResourceBuildItem.class.getName(),
                GeneratedTektonResourceBuildItem.class.getName(),
        };
    }

    /**
     * Specifies if resources should be overwritten if they already exists
     * Command that directly generate resources in most cases should return true.
     * Commands that need generated resources should only generate if they don't exist.
     *
     * @return true if should overwrite, false otherwise
     */
    public boolean shouldOverwrite() {
        return true;
    }

    /**
     * The tekton output directory.
     *
     * @return the specified tekton output directory or the default one (.tekton)
     */
    public Path getTektonOutputDir() {
        return tektonOutpuDir.orElseGet(() -> Projects.getProjectRoot().resolve(".tekton"));
    }

    public List<HasMetadata> readExistingTektonResources() {
        Path tektonOutputDir = getTektonOutputDir();
        Path tektonYaml = tektonOutputDir.resolve("tekton.yml");
        try (FileInputStream is = new FileInputStream(tektonYaml.toFile())) {
            List<HasMetadata> resources = Serialization.unmarshalAsList(is).getItems();
            return resources;
        } catch (IOException e) {
            System.out.println("Could not read resources" + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public Properties getBuildSystemProperties() {
        Properties buildSystemProperties = new Properties();
        Path projectRoot = getWorkingDirectory();
        Path applicationPropertiesPath = projectRoot.resolve("src").resolve("main").resolve("resources")
                .resolve("application.properties");
        if (Files.exists(applicationPropertiesPath)) {
            try {
                buildSystemProperties.load(Files.newBufferedReader(applicationPropertiesPath));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        namespace.ifPresent(v -> buildSystemProperties.setProperty("quarkus.tekton.namespace", v));
        return buildSystemProperties;
    }

    public List<Dependency> getProjectDependencies() {
        List<Dependency> dependencies = new ArrayList<>();
        try {
            BootstrapAppModelFactory.newInstance()
                    .setProjectRoot(getWorkingDirectory())
                    .setLocalProjectsDiscovery(true)
                    .resolveAppModel()
                    .getApplicationModel()
                    .getDependencies().forEach(d -> {
                        dependencies.add(new ArtifactDependency(d.getGroupId(), d.getArtifactId(), d.getClassifier(),
                                d.getType(), d.getVersion()));
                    });
        } catch (BootstrapException e) {
            //Ignore, as it's currently broken for gradle
        }

        if (!dependencies.stream()
                .anyMatch(d -> d.getGroupId().equals("io.quarkiverse.tekton") && d.getArtifactId().equals("quarkus-tekton"))) {
            dependencies.add(QUARKUS_TEKTON);
        }
        return dependencies;
    }

    public Integer call() {
        if (!shouldOverwrite() && Files.exists(getTektonOutputDir())) {
            GenerationBaseCommand.this.process(readExistingTektonResources());
            return ExitCode.OK;
        }

        Path projectRoot = getWorkingDirectory();
        BuildTool buildTool = QuarkusProjectHelper.detectExistingBuildTool(projectRoot);
        Path targetDirecotry = projectRoot.resolve(buildTool.getBuildDirectory());
        QuarkusBootstrap quarkusBootstrap = QuarkusBootstrap.builder()
                .setMode(QuarkusBootstrap.Mode.PROD)
                .setBuildSystemProperties(getBuildSystemProperties())
                .setApplicationRoot(getWorkingDirectory())
                .setProjectRoot(getWorkingDirectory())
                .setTargetDirectory(targetDirecotry)
                .setIsolateDeployment(false)
                .setRebuild(true)
                .setTest(false)
                .setLocalProjectDiscovery(true)
                .setBaseClassLoader(ClassLoader.getSystemClassLoader())
                .setForcedDependencies(getProjectDependencies())
                .build();

        // Checking
        try (CuratedApplication curatedApplication = quarkusBootstrap.bootstrap()) {
            AugmentAction action = curatedApplication.createAugmentor();
            action.performCustomBuild(TektonHandler.class.getName(), new TektonResourcesProcessor() {
                @Override
                public void process(List<HasMetadata> resources) {
                    GenerationBaseCommand.this.process(resources);
                }
            }, getRequiredBuildItems());

        } catch (BootstrapException e) {
            throw new RuntimeException(e);
        }
        return ExitCode.OK;
    }

    public Optional<String> getNamespace() {
        return namespace;
    }

    protected void writeStringSafe(Path p, String content) {
        try {
            if (!Files.exists(p.getParent())) {
                Files.createDirectories(p.getParent());
            }
            Files.writeString(p, content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected Path getWorkingDirectory() {
        return Paths.get(System.getProperty("user.dir"));
    }

    private static String getVersion() {
        return Resources.read("/version").trim();
    }
}
