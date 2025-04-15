package io.quarkiverse.tekton.deployment;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.tekton.v1.Pipeline;
import io.quarkiverse.tekton.cm.MavenSettingsCm;
import io.quarkiverse.tekton.common.utils.Serialization;
import io.quarkiverse.tekton.pipeline.BuildTestPushPipeline;
import io.quarkiverse.tekton.pipelinerun.BuildTestPushPipelineRun;
import io.quarkiverse.tekton.pvc.MavenRepoPvc;
import io.quarkiverse.tekton.pvc.ProjectWorkspacePvc;
import io.quarkiverse.tekton.spi.GeneratedTektonResourceBuildItem;
import io.quarkiverse.tekton.task.BuildahTask;
import io.quarkiverse.tekton.task.GitCloneTask;
import io.quarkiverse.tekton.task.LsTask;
import io.quarkiverse.tekton.task.MavenTask;
import io.quarkiverse.tekton.task.RmTask;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedFileSystemResourceBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.kubernetes.spi.GeneratedKubernetesResourceBuildItem;

public class TektonProcessor {

    public static final String FEATURE = "tekton";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void generateCatalogInfo(TektonConfiguration config,
            ApplicationInfoBuildItem applicationInfo,
            OutputTargetBuildItem outputTarget,
            BuildProducer<GeneratedTektonResourceBuildItem> generatedTektonResources) {

        String name = applicationInfo.getName();
        List<HasMetadata> resources = new ArrayList<>();

        // ConfigMaps
        resources.add(MavenSettingsCm.create(name));

        // PVCs
        resources.add(ProjectWorkspacePvc.create(name));
        resources.add(MavenRepoPvc.create(name));

        // Tasks
        resources.add(LsTask.create());
        resources.add(RmTask.create());
        resources.add(GitCloneTask.create());
        resources.add(MavenTask.create());
        resources.add(BuildahTask.create());

        // Pipelines
        Pipeline aPipeline = new BuildTestPushPipeline().create();
        resources.add(aPipeline);

        // PipelineRun
        if (config.pipelinerun().enabled()) {
            resources.add(new BuildTestPushPipelineRun().create(name, aPipeline, Optional.of(config.pipelinerun().params())));
        }

        generatedTektonResources.produce(new GeneratedTektonResourceBuildItem(resources));
    }

    @BuildStep(onlyIf = IsTektonResourcesGenerationEnabled.class)
    public void addInKubernetes(TektonConfiguration configuration,
            OutputTargetBuildItem outputTarget,
            List<GeneratedTektonResourceBuildItem> generatedTektonResources,
            BuildProducer<GeneratedFileSystemResourceBuildItem> generatedFileSystemResources,
            BuildProducer<GeneratedKubernetesResourceBuildItem> generatedKubernetesResources) {

        Path tektonOutputPath = outputTarget.getOutputDirectory().resolve("kubernetes");

        List<HasMetadata> allTektonResources = generatedTektonResources.stream().flatMap(r -> r.getResources().stream())
                .collect(Collectors.toList());
        //String yaml = Serialization.asYaml(allTektonResources);
        String json = Serialization.asYaml(allTektonResources);

        // Iterate through the list of the resources to process them individually to include ---
        // as separator between them instead of a list of items
        StringBuilder yamlOutput = new StringBuilder();
        for (HasMetadata resource : allTektonResources) {
            String yaml = io.fabric8.kubernetes.client.utils.Serialization.asYaml(resource).trim();
            yamlOutput.append(yaml).append("\n");
        }

        Path resourcePathYaml = tektonOutputPath.resolve("tekton.yaml");
        generatedFileSystemResources
                .produce(new GeneratedFileSystemResourceBuildItem(resourcePathYaml.toString(),
                        yamlOutput.toString().getBytes()));
        generatedKubernetesResources
                .produce(new GeneratedKubernetesResourceBuildItem(resourcePathYaml.toString(),
                        yamlOutput.toString().getBytes()));

        Path resourcePathJson = tektonOutputPath.resolve("tekton.json");
        generatedFileSystemResources
                .produce(new GeneratedFileSystemResourceBuildItem(resourcePathJson.toString(), json.getBytes()));
        generatedKubernetesResources
                .produce(new GeneratedKubernetesResourceBuildItem(resourcePathJson.toString(), json.getBytes()));
    }
}
