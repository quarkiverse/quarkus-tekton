package io.quarkiverse.tekton.pipeline;

import io.fabric8.tekton.v1.Pipeline;
import io.quarkiverse.tekton.common.utils.Resources;
import io.quarkiverse.tekton.common.utils.Serialization;

public class BuildTestPushPipeline {

    public static Pipeline create() {
        Pipeline pipeline = Serialization.unmarshal(Resources.read("/tekton/pipelines/build-test-push.yaml"));
        return pipeline;
    }
}
