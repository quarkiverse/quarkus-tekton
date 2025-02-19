package io.quarkiverse.tekton.task;

import io.fabric8.tekton.v1.Task;
import io.quarkiverse.tekton.common.utils.Resources;
import io.quarkiverse.tekton.common.utils.Serialization;

public class MavenTask {

    public static Task create() {
        return Serialization.unmarshal(Resources.read("/tekton/tasks/catalog/maven.yaml"));
    }
}
