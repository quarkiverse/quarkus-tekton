package io.quarkiverse.tekton.task;

import io.fabric8.tekton.v1.Task;
import io.fabric8.tekton.v1.TaskBuilder;

public class LsTask {

    public static Task create() {
        return new TaskBuilder()
                .withNewMetadata()
                .withName("ls")
                .endMetadata()
                .withNewSpec()
                .addNewParam()
                .withName("args")
                .withType("string")
                .withDescription("The args to pass to ls")
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
                .withArgs("ls $(params.args)")
                .endStep()
                .endSpec()
                .build();
    }
}
