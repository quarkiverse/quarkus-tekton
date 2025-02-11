package io.quarkiverse.tekton.common.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class Strings {

    public static String read(Path path) {
        return read(path.toFile());
    }

    public static String read(File file) {
        try (InputStream is = new FileInputStream(file)) {
            return read(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String read(InputStream is) {
        try {
            return new String(is.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
