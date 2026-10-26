package io.quarkiverse.tekton.cli.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.quarkiverse.tekton.task.ParamDefinition;
import io.quarkiverse.tekton.task.WorkspaceDefinition;

class TaskOptionsTest {

    @Test
    void parsesParams() {
        assertEquals(new ParamDefinition("who", "string", Optional.empty(), Optional.empty()),
                TaskOptions.parseParam("who"));
        assertEquals(new ParamDefinition("who", "string", Optional.of("world"), Optional.empty()),
                TaskOptions.parseParam("who=world"));
        assertEquals(new ParamDefinition("labels", "object", Optional.empty(), Optional.empty()),
                TaskOptions.parseParam("labels:object"));
        assertEquals(new ParamDefinition("files", "array", Optional.of("a,b"), Optional.empty()),
                TaskOptions.parseParam("files:array=a,b"));
        assertEquals(new ParamDefinition("labels", "object", Optional.of("app=demo,url=http://x:8080"), Optional.empty()),
                TaskOptions.parseParam("labels:object=app=demo,url=http://x:8080"));
    }

    @Test
    void rejectsInvalidParams() {
        for (String value : List.of("", ":object", "=world", "who:", "who:object:x", "who=")) {
            assertThrows(IllegalArgumentException.class, () -> TaskOptions.parseParam(value), value);
        }
    }

    @Test
    void addsTheDescriptionAndPropertiesOfParams() {
        List<ParamDefinition> params = TaskOptions.parseParams(List.of("who=world", "labels:object"),
                List.of("who=Who to greet"), List.of("labels=app, tier"));

        assertEquals(List.of(
                new ParamDefinition("labels", "object", Optional.empty(), Optional.empty(), List.of("app", "tier")),
                new ParamDefinition("who", "string", Optional.of("world"), Optional.of("Who to greet"))), params);
    }

    @Test
    void rejectsDescriptionsAndPropertiesOfUndeclaredParams() {
        assertThrows(IllegalArgumentException.class,
                () -> TaskOptions.parseParams(List.of("who"), List.of("other=description"), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> TaskOptions.parseParams(List.of("labels:object"), List.of(), List.of("other=app")));
        assertThrows(IllegalArgumentException.class,
                () -> TaskOptions.parseParams(List.of("labels:object"), List.of(), List.of("labels=")));
        assertThrows(IllegalArgumentException.class,
                () -> TaskOptions.parseParams(List.of("who", "who=world"), List.of(), List.of()));
    }

    @Test
    void parsesWorkspaces() {
        assertEquals(new WorkspaceDefinition("source", Optional.empty(), false, Optional.empty()),
                TaskOptions.parseWorkspace("source"));
        assertEquals(new WorkspaceDefinition("source", Optional.of("/workspace/src"), false, Optional.empty()),
                TaskOptions.parseWorkspace("source:/workspace/src"));
        assertEquals(new WorkspaceDefinition("source", Optional.empty(), true, Optional.empty()),
                TaskOptions.parseWorkspace("source:optional"));
        assertEquals(new WorkspaceDefinition("source", Optional.empty(), true, Optional.empty()),
                TaskOptions.parseWorkspace("source::optional"));
        assertEquals(new WorkspaceDefinition("source", Optional.of("/src"), true, Optional.empty()),
                TaskOptions.parseWorkspace("source:/src:optional"));
    }

    @Test
    void rejectsInvalidWorkspaces() {
        for (String value : List.of("", ":/src", "source:/src:required", "source:/src:optional:x")) {
            assertThrows(IllegalArgumentException.class, () -> TaskOptions.parseWorkspace(value), value);
        }
    }

    @Test
    void parsesKeyValues() {
        assertEquals(Map.of("GREETING", "hello=world", "EMPTY", ""),
                TaskOptions.parseKeyValues("--env", List.of("GREETING=hello=world", "EMPTY=")));
        assertThrows(IllegalArgumentException.class, () -> TaskOptions.parseKeyValues("--env", List.of("GREETING")));
        assertThrows(IllegalArgumentException.class, () -> TaskOptions.parseKeyValues("--env", List.of("=value")));
        assertThrows(IllegalArgumentException.class,
                () -> TaskOptions.parseKeyValues("--env", List.of("A=1", "A=2")));
    }
}
