package io.quarkiverse.tekton.deployment;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusProdModeTest;

class InvalidCustomTaskConfigTest {

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName("invalid-custom-task")
            .setApplicationVersion("0.1-SNAPSHOT")
            .setRun(false)
            .overrideConfigKey("quarkus.tekton.task.hello.image", "registry.access.redhat.com/ubi9/ubi-minimal")
            .overrideConfigKey("quarkus.tekton.task.hello.script", "echo hello")
            .overrideConfigKey("quarkus.tekton.task.hello.command", "sh,-c")
            .assertBuildException(e -> {
                Throwable cause = e;
                while (cause != null && !(cause instanceof IllegalArgumentException)) {
                    cause = cause.getCause();
                }
                assertNotNull(cause, "Expected an IllegalArgumentException, got: " + e);
                assertTrue(cause.getMessage().contains("quarkus.tekton.task.\"hello\""), cause.getMessage());
                assertTrue(cause.getMessage().contains("both a script and a command"), cause.getMessage());
            });

    @Test
    void testBuildFails() {
        fail("The build should have failed");
    }
}
