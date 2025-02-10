package io.quarkiverse.tekton.task;

import io.fabric8.tekton.v1.Task;
import io.fabric8.tekton.v1.TaskBuilder;

public class RmTask {

    public static Task create() {
        return new TaskBuilder()
                .withNewMetadata()
                .withName("rm")
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
                .withName("ls")
                .withImage("quay.io/redhat-cop/ubi8-git:v1.0")
                .withWorkingDir("$(workspaces.project-dir.path)")
                .withCommand("sh", "-c")
                .withArgs("rm $(params.args)")
                .endStep()
                .endSpec()
                .build();
    }
}
