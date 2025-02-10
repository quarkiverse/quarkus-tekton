package io.quarkiverse.tekton.common.handler;

import java.util.List;

public interface ResourcesProcessor<T> {

    void process(List<T> resources);
}
