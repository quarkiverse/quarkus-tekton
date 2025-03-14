package io.quarkiverse.tekton.common.utils;

public final class Resources {

    private Resources() {
        // Utility class
    }

    public static String read(String path) {
        try {
            return new String(Resources.class.getResourceAsStream(path).readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
