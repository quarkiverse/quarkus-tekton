package io.quarkiverse.tekton.cli.pipeline;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.tekton.cli.common.GenerationBaseCommand;
import io.quarkiverse.tekton.cli.common.OutputOptionMixin;
import io.quarkiverse.tekton.common.utils.Clients;
import picocli.CommandLine.Mixin;

public abstract class AbstractPipelineCommand extends GenerationBaseCommand {

    @Mixin(name = "output")
    OutputOptionMixin output;

    private Map<String, io.fabric8.tekton.v1.Pipeline> installedV1Pipelines = new HashMap<>();
    private Map<String, io.fabric8.tekton.v1beta1.Pipeline> installedV1beta1Pipelines = new HashMap<>();

    private Map<String, io.fabric8.tekton.v1.Pipeline> projectV1Pipelines = new HashMap<>();
    private Map<String, io.fabric8.tekton.v1beta1.Pipeline> projectV1beta1Pipelines = new HashMap<>();

    @Inject
    KubernetesClient kubernetesClient;

    void readInstalledPipelines() {
        try {
            Clients.use(kubernetesClient);

            Clients.tekton().v1().pipelines().list().getItems().forEach(t -> {
                installedV1Pipelines.put(t.getMetadata().getName(), t);
            });

            Clients.tekton().v1beta1().pipelines().list().getItems().forEach(t -> {
                installedV1beta1Pipelines.put(t.getMetadata().getName(), t);
            });
        } catch (Exception e) {
            // ignore
        }
    }

    void addInstalledPipeline(io.fabric8.tekton.v1.Pipeline pipeline) {
        installedV1Pipelines.put(pipeline.getMetadata().getName(), pipeline);
    }

    void addInstalledPipeline(io.fabric8.tekton.v1beta1.Pipeline pipeline) {
        installedV1beta1Pipelines.put(pipeline.getMetadata().getName(), pipeline);
    }

    io.fabric8.tekton.v1.Pipeline removeInstalledPipeline(io.fabric8.tekton.v1.Pipeline pipeline) {
        return installedV1Pipelines.remove(pipeline.getMetadata().getName());
    }

    io.fabric8.tekton.v1beta1.Pipeline removeInstalledPipeline(io.fabric8.tekton.v1beta1.Pipeline pipeline) {
        return installedV1beta1Pipelines.remove(pipeline.getMetadata().getName());
    }

    Set<String> getInstalledPipelineNames() {
        return Stream.concat(installedV1Pipelines.keySet().stream(), installedV1beta1Pipelines.keySet().stream())
                .collect(Collectors.toSet());
    }

    boolean isInstalled(String pipelineName) {
        return installedV1Pipelines.keySet().contains(pipelineName)
                || installedV1beta1Pipelines.keySet().contains(pipelineName);
    }

    Optional<HasMetadata> getInstalledPipeline(String pipelineName) {
        if (installedV1beta1Pipelines.containsKey(pipelineName)) {
            return Optional.of(installedV1beta1Pipelines.get(pipelineName));
        }
        if (installedV1Pipelines.containsKey(pipelineName)) {
            return Optional.of(installedV1Pipelines.get(pipelineName));
        }
        return Optional.empty();
    }

    void readProjectPipelines(List<HasMetadata> resources) {
        try {
            for (HasMetadata resource : resources) {
                if (resource instanceof io.fabric8.tekton.v1.Pipeline v1Pipeline) {
                    projectV1Pipelines.put(v1Pipeline.getMetadata().getName(), v1Pipeline);
                }

                if (resource instanceof io.fabric8.tekton.v1beta1.Pipeline v1beta1Pipeline) {
                    projectV1beta1Pipelines.put(v1beta1Pipeline.getMetadata().getName(), v1beta1Pipeline);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    Set<String> getProjectPipelineNames() {
        return Stream.concat(projectV1Pipelines.keySet().stream(), projectV1beta1Pipelines.keySet().stream())
                .collect(Collectors.toSet());
    }

    boolean isProject(String pipelineName) {
        return projectV1Pipelines.keySet().contains(pipelineName) || projectV1beta1Pipelines.keySet().contains(pipelineName);
    }

    Optional<HasMetadata> getProjectPipeline(String pipelineName) {
        if (projectV1beta1Pipelines.containsKey(pipelineName)) {
            return Optional.of(projectV1beta1Pipelines.get(pipelineName));
        }
        if (projectV1Pipelines.containsKey(pipelineName)) {
            return Optional.of(projectV1Pipelines.get(pipelineName));
        }
        return Optional.empty();
    }

    public List<PipelineListItem> getPipelineListItems() {
        List<PipelineListItem> items = new ArrayList<>();
        for (String pipelineName : projectV1beta1Pipelines.keySet()) {
            items.add(new PipelineListItem("tekton.dev/v1beta1", "Pipeline", pipelineName, isInstalled(pipelineName), true));
        }

        for (String pipelineName : projectV1Pipelines.keySet()) {
            items.add(new PipelineListItem("tekton.dev/v1", "Pipeline", pipelineName, isInstalled(pipelineName), true));
        }
        return items;
    }

    public List<PipelineListItem> getPipelineListItems(Predicate<PipelineListItem> predicate) {
        List<PipelineListItem> items = new ArrayList<>();
        for (String pipelineName : projectV1beta1Pipelines.keySet()) {
            items.add(new PipelineListItem("tekton.dev/v1beta1", "Pipeline", pipelineName, isInstalled(pipelineName), true));
        }

        for (String pipelineName : projectV1Pipelines.keySet()) {
            items.add(new PipelineListItem("tekton.dev/v1", "Pipeline", pipelineName, isInstalled(pipelineName), true));
        }
        return items.stream().filter(predicate).collect(Collectors.toList());
    }

    boolean isPipeline(HasMetadata resource) {
        if (!"Pipeline".equals(resource.getKind())) {
            return false;
        }
        if (!"tekton.dev/v1beta1".equals(resource.getApiVersion()) && !"tekton.dev/v1".equals(resource.getApiVersion())) {
            return false;
        }
        return true;
    }
}
