package io.quarkiverse.tekton.task;

import io.fabric8.tekton.v1.Task;
import io.fabric8.tekton.v1.TaskBuilder;

public class GitCloneTask {

    public static Task create() {
        return new TaskBuilder()
                .withNewMetadata()
                .withName("git-clone")
                .endMetadata()
                .withNewSpec()
                .addNewParam()
                .withName("url")
                .withType("string")
                .withDescription("The git repository url")
                .endParam()
                .addNewWorkspace()
                .withName("project-dir")
                .withDescription("A workspace for the task")
                .withOptional(true)
                .withMountPath("/mnt/workspace")
                .endWorkspace()
                .addNewStep()
                .withName("clone")
                .withImage("quay.io/redhat-cop/ubi8-git:v1.0")
                .withWorkingDir("$(workspaces.project-dir.path)")
                .withCommand("sh", "-c")
                .withArgs("git clone $(params.url) .")
                .endStep()
                .endSpec()
                .build();
    }
}
