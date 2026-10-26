package io.quarkiverse.tekton.cli.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.tekton.cli.common.ApplicationProperties;
import picocli.CommandLine;

class TaskGenerateTest {

    @TempDir
    Path projectRoot;

    StringWriter out = new StringWriter();

    @BeforeEach
    void createProject() throws IOException {
        Files.writeString(projectRoot.resolve("pom.xml"), "<project/>");
    }

    private int execute(String... args) {
        CommandLine commandLine = new CommandLine(new TaskGenerate() {
            @Override
            protected Path getWorkingDirectory() {
                return projectRoot;
            }
        });
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(out, true));
        return commandLine.execute(args);
    }

    @Test
    void rejectsScriptAndCommand() {
        int exitCode = execute("hello", "--image", "ubi", "--script", "echo hello", "--command", "sh,-c");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        assertTrue(out.toString().contains("mutually exclusive"), out.toString());
        assertFalse(Files.exists(ApplicationProperties.path(projectRoot)));
    }

    @Test
    void doesNotWriteAnInvalidTask() {
        int exitCode = execute("Hello", "--image", "ubi", "--script", "echo hello");

        assertNotEquals(CommandLine.ExitCode.OK, exitCode);
        assertTrue(out.toString().contains("Invalid task name: Hello"), out.toString());
        assertFalse(Files.exists(ApplicationProperties.path(projectRoot)));
    }

    @Test
    void doesNotWriteATaskWithoutScriptOrCommand() {
        int exitCode = execute("hello", "--image", "ubi");

        assertNotEquals(CommandLine.ExitCode.OK, exitCode);
        assertTrue(out.toString().contains("needs either a script or a command"), out.toString());
        assertFalse(Files.exists(ApplicationProperties.path(projectRoot)));
    }

    @Test
    void restoresTheApplicationPropertiesWhenTheRegenerationFails() throws IOException {
        Path file = ApplicationProperties.path(projectRoot);
        Files.createDirectories(file.getParent());
        String content = "# My application\nquarkus.tekton.task.hello.image=old\n";
        Files.writeString(file, content);

        // the empty pom.xml is not a buildable project
        int exitCode = execute("hello", "--image", "ubi", "--script", "echo hello");

        assertNotEquals(CommandLine.ExitCode.OK, exitCode);
        assertTrue(out.toString().contains("was restored"), out.toString());
        assertEquals(content, Files.readString(file));
    }

    @Test
    void deletesTheCreatedApplicationPropertiesWhenTheRegenerationFails() {
        int exitCode = execute("hello", "--image", "ubi", "--script", "echo hello");

        assertNotEquals(CommandLine.ExitCode.OK, exitCode);
        assertTrue(out.toString().contains("was restored"), out.toString());
        assertFalse(Files.exists(ApplicationProperties.path(projectRoot)));
    }

    @Test
    void printsTheTaskOnDryRun() {
        int exitCode = execute("list-files", "--image", "quay.io/redhat-cop/ubi8-git:v1.0", "--command", "sh,-c",
                "--args", "ls -la", "--param", "dir=/tmp", "-w", "source::optional", "--dry-run");

        assertEquals(CommandLine.ExitCode.OK, exitCode, out.toString());
        String output = out.toString();
        assertTrue(output.contains("quarkus.tekton.task.list-files.command=sh,-c"), output);
        assertTrue(output.contains("quarkus.tekton.task.list-files.args=ls -la"), output);
        assertTrue(output.contains("quarkus.tekton.task.list-files.param.dir.default=/tmp"), output);
        assertTrue(output.contains("quarkus.tekton.task.list-files.workspace.source.optional=true"), output);
        assertTrue(output.contains("kind: Task"), output);
        assertFalse(Files.exists(ApplicationProperties.path(projectRoot)));
    }
}
