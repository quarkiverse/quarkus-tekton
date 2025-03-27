package io.quarkiverse.tekton.pipeline;

import io.fabric8.tekton.v1.Pipeline;
import io.fabric8.tekton.v1.PipelineBuilder;
import io.quarkiverse.tekton.common.utils.Resources;
import io.quarkiverse.tekton.common.utils.Serialization;
import io.quarkiverse.tekton.visitors.AddParamSpecDefaultValue;

public class BuildTestPushPipeline {

    public static Pipeline create() {
        Pipeline pipeline = Serialization.unmarshal(Resources.read("/tekton/pipelines/build-test-push.yaml"));
        pipeline = new PipelineBuilder(pipeline)
                .accept(
                        new AddParamSpecDefaultValue("DOCKERFILE", "src/main/docker/Dockerfile.jvm"))
                .build();
        return pipeline;
    }
}
