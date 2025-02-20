package io.quarkiverse.tekton.task;

import io.fabric8.tekton.v1.Task;
import io.fabric8.tekton.v1.TaskBuilder;
import io.quarkiverse.tekton.common.utils.Resources;
import io.quarkiverse.tekton.common.utils.Serialization;
import io.quarkiverse.tekton.visitors.AddParamSpecDefaultValue;

public class BuildahTask {

    public static Task create() {
        Task task = Serialization.unmarshal(Resources.read("/tekton/tasks/buildah.yaml"));
        task = new TaskBuilder(task)
                .accept(new AddParamSpecDefaultValue("DOCKERFILE", "src/main/docker/Dockerfile.jvm"))
                .build();
        return task;
    }
}
