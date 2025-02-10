package io.quarkiverse.tekton.common.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public final class Projects {

    private static final String[] BUILD_FILES = { "pom.xml", "build.gradle", "build.gradle.kts" };

    public static Path getProjectRoot() {
        return getProjectRoot(Paths.get(System.getProperty("user.dir")));
    }

    /**
     * Get the root {@link Path} of the project.
     * Iterates over the parent directories and returns the last directory that contains a build file.
     * If no build file is found, the current directory is returned.
     *
     * @param dir the directory to start the search from
     * @return the root directory of the project
     */
    public static Path getProjectRoot(Path dir) {
        Optional<Path> scmRoot = Git.getScmRoot(dir);
        if (scmRoot.isPresent()) {
            return scmRoot.get();
        }

        Path currentDir = dir;
        Path lastProjectDir = null;
        while (currentDir != null) {
            boolean buildFileFound = false;
            for (String buildFile : BUILD_FILES) {
                if (currentDir.resolve(buildFile).toFile().exists()) {
                    lastProjectDir = currentDir;
                    buildFileFound = true;
                }
            }

            if (!buildFileFound && lastProjectDir != null) {
                return lastProjectDir;
            }
            currentDir = currentDir.getParent();
        }
        return dir;
    }

    /**
     * Get the root {@link Path} of the module.
     * Iterates over the parent directories and returns the first directory that contains a build file.
     * If no build file is found, the current directory is returned.
     *
     * @param dir the directory to start the search from
     * @return the root directory of the module
     */
    public static Path getModuleRoot(Path dir) {
        Path currentDir = dir;
        while (currentDir != null && !currentDir.resolve(".git").toFile().exists()) {
            for (String buildFile : BUILD_FILES) {
                if (currentDir.resolve(buildFile).toFile().exists()) {
                    return currentDir;
                }
            }
            currentDir = currentDir.getParent();
        }
        return currentDir;
    }
}
