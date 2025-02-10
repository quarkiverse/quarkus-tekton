package io.quarkiverse.tekton.common;

import java.io.InputStream;

public class TektonResources {

    private static final String TEKTON_PATH = "/tekton/";
    private static final String TASK_PREFIX = "task-";

    private static InputStream read(String path) {
        return TektonResources.class.getResourceAsStream(path);
    }
}
