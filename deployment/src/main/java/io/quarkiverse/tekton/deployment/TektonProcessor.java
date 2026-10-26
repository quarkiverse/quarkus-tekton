package io.quarkiverse.tekton.deployment;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.tekton.v1.Pipeline;
import io.fabric8.tekton.v1.Task;
import io.quarkiverse.tekton.cm.MavenSettingsCm;
import io.quarkiverse.tekton.common.utils.Serialization;
import io.quarkiverse.tekton.deployment.TektonConfiguration.CustomTaskConfig;
import io.quarkiverse.tekton.pipeline.BuildTestPushPipeline;
import io.quarkiverse.tekton.pipelinerun.BuildTestPushPipelineRun;
import io.quarkiverse.tekton.pvc.MavenRepoPvc;
import io.quarkiverse.tekton.pvc.ProjectWorkspacePvc;
import io.quarkiverse.tekton.spi.GeneratedTektonResourceBuildItem;
import io.quarkiverse.tekton.task.BuildahTask;
import io.quarkiverse.tekton.task.CustomTask;
import io.quarkiverse.tekton.task.GitCloneTask;
import io.quarkiverse.tekton.task.LsTask;
import io.quarkiverse.tekton.task.MavenTask;
import io.quarkiverse.tekton.task.ParamDefinition;
import io.quarkiverse.tekton.task.RmTask;
import io.quarkiverse.tekton.task.StepDefinition;
import io.quarkiverse.tekton.task.TaskDefinition;
import io.quarkiverse.tekton.task.WorkspaceDefinition;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedFileSystemResourceBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.kubernetes.spi.GeneratedKubernetesResourceBuildItem;

public class TektonProcessor {

    public static final String FEATURE = "tekton";

    private static final Logger LOG = Logger.getLogger(TektonProcessor.class);

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
        List<Task> tasks = new ArrayList<>(List.of(LsTask.create(), RmTask.create(), GitCloneTask.create(),
                MavenTask.create(), BuildahTask.create()));
        addCustomTasks(config, tasks);
        resources.addAll(tasks);

        // Pipelines
        Pipeline aPipeline = BuildTestPushPipeline.create();
        resources.add(aPipeline);

        // PipelineRun
        if (config.pipelinerun().enabled()) {
            resources.add(BuildTestPushPipelineRun.create(name, aPipeline, Optional.of(config.pipelinerun().params()),
                    config.pipelinerun().registryAuthSecret(), config.pipelinerun().sslCaConfigMap()));
        }

        generatedTektonResources.produce(new GeneratedTektonResourceBuildItem(resources));
    }

    /**
     * Adds the tasks declared using {@code quarkus.tekton.task.<name>.*}. A custom task having the same name as a
     * provided task replaces it.
     */
    static void addCustomTasks(TektonConfiguration config, List<Task> tasks) {
        // config maps have no guaranteed order, sort them to generate stable resources
        new TreeMap<>(config.tasks()).forEach((name, taskConfig) -> {
            Task task;
            try {
                task = CustomTask.create(toDefinition(name, taskConfig));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid custom task configured using quarkus.tekton.task.\"" + name + "\": " + e.getMessage(), e);
            }
            for (int i = 0; i < tasks.size(); i++) {
                if (name.equals(tasks.get(i).getMetadata().getName())) {
                    LOG.warnf("The custom task %s configured using quarkus.tekton.task.\"%s\" replaces the provided task %s.",
                            name, name, name);
                    tasks.set(i, task);
                    return;
                }
            }
            tasks.add(task);
        });
    }

    static TaskDefinition toDefinition(String name, CustomTaskConfig config) {
        List<ParamDefinition> params = new ArrayList<>();
        new TreeMap<>(config.param()).forEach((paramName, param) -> params.add(new ParamDefinition(paramName,
                param.type(), param.defaultValue(), param.description(), param.properties().orElse(List.of()))));

        List<WorkspaceDefinition> workspaces = new ArrayList<>();
        new TreeMap<>(config.workspace()).forEach((workspaceName, workspace) -> workspaces.add(new WorkspaceDefinition(
                workspaceName, workspace.mountPath(), workspace.optional(), workspace.description())));

        Map<String, String> env = new TreeMap<>(config.env());
        StepDefinition step = new StepDefinition(null, config.image(), config.script(), config.command().orElse(List.of()),
                config.args().orElse(List.of()), config.workingDir(), env);

        return new TaskDefinition(name, config.description(), params, workspaces, List.of(step));
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
        String yaml = Serialization.asYaml(allTektonResources);
        String json = Serialization.asJson(allTektonResources);

        Path resourcePathYaml = tektonOutputPath.resolve("tekton.yaml");
        generatedFileSystemResources
                .produce(new GeneratedFileSystemResourceBuildItem(resourcePathYaml.toString(),
                        yaml.getBytes()));
        generatedKubernetesResources
                .produce(new GeneratedKubernetesResourceBuildItem(resourcePathYaml.toString(),
                        yaml.getBytes()));

        Path resourcePathJson = tektonOutputPath.resolve("tekton.json");
        generatedFileSystemResources
                .produce(new GeneratedFileSystemResourceBuildItem(resourcePathJson.toString(), json.getBytes()));
        generatedKubernetesResources
                .produce(new GeneratedKubernetesResourceBuildItem(resourcePathJson.toString(), json.getBytes()));
    }
}
