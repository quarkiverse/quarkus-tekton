package io.quarkiverse.tekton.cli.pipeline;

import io.quarkiverse.tekton.cli.common.ListItem;

public class PipelineListItem implements ListItem {

    private String apiVersion;
    private String kind;
    private String name;
    private String inCluster;
    private String inProject;

    public PipelineListItem(String apiVersion, String kind, String name, boolean inCluster, boolean inProject) {
        this(apiVersion, kind, name,
                inCluster ? "    *    " : "         ",
                inProject ? "    *    " : "         ");
    }

    public PipelineListItem(String apiVersion, String kind, String name, String inCluster, String inProject) {
        this.apiVersion = apiVersion;
        this.kind = kind;
        this.name = name;
        this.inCluster = inCluster;
        this.inProject = inProject;
    }

    public static PipelineListItem from(io.fabric8.tekton.v1.Pipeline pipeline, boolean inCluster, boolean inProject) {
        return new PipelineListItem(pipeline.getApiVersion(), pipeline.getKind(), pipeline.getMetadata().getName(), inCluster,
                inProject);
    }

    public static PipelineListItem from(io.fabric8.tekton.v1.Pipeline pipeline, String inCluster, String inProject) {
        return new PipelineListItem(pipeline.getApiVersion(), pipeline.getKind(), pipeline.getMetadata().getName(), inCluster,
                inProject);
    }

    public static PipelineListItem from(io.fabric8.tekton.v1beta1.Pipeline pipeline, boolean inCluster, boolean inProject) {
        return new PipelineListItem(pipeline.getApiVersion(), pipeline.getKind(), pipeline.getMetadata().getName(), inCluster,
                inProject);
    }

    public static PipelineListItem from(io.fabric8.tekton.v1beta1.Pipeline pipeline, String inCluster, String inProject) {
        return new PipelineListItem(pipeline.getApiVersion(), pipeline.getKind(), pipeline.getMetadata().getName(), inCluster,
                inProject);
    }

    public String[] getFields() {
        return new String[] { apiVersion, name, inProject, inCluster };
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String isInCluster() {
        return inCluster;
    }

    public void setInCluster(String inCluster) {
        this.inCluster = inCluster;
    }

    public String isInProject() {
        return inProject;
    }

    public void setInProject(String inProject) {
        this.inProject = inProject;
    }
}
