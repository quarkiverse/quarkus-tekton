package io.quarkiverse.tekton.pipeline;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.tekton.v1.Param;
import io.fabric8.tekton.v1.ParamBuilder;
import io.fabric8.tekton.v1.ParamSpec;
import io.fabric8.tekton.v1.ParamSpecBuilder;
import io.fabric8.tekton.v1.ParamValue;
import io.fabric8.tekton.v1.Pipeline;
import io.fabric8.tekton.v1.PipelineBuilder;
import io.fabric8.tekton.v1.PipelineTask;
import io.fabric8.tekton.v1.PipelineTaskBuilder;
import io.fabric8.tekton.v1.PipelineWorkspaceDeclaration;
import io.fabric8.tekton.v1.PipelineWorkspaceDeclarationBuilder;
import io.fabric8.tekton.v1.Task;
import io.fabric8.tekton.v1.WorkspaceDeclaration;
import io.fabric8.tekton.v1.WorkspacePipelineTaskBinding;
import io.fabric8.tekton.v1.WorkspacePipelineTaskBindingBuilder;

/**
 * Creates a Pipeline running the given tasks sequentially (each task runs after the previous one).
 * The params and workspaces of the tasks are exposed as params and workspaces of the pipeline.
 * When two tasks declare a param with the same name but a different type, the param of the latter is prefixed
 * with the name of its task.
 */
public class TaskSequencePipeline {

    public static Pipeline create(String name, List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("At least one task is needed to create the pipeline " + name + ".");
        }

        Map<String, ParamSpec> pipelineParams = new LinkedHashMap<>();
        Map<String, PipelineWorkspaceDeclaration> pipelineWorkspaces = new LinkedHashMap<>();
        List<PipelineTask> pipelineTasks = new ArrayList<>();
        Set<String> pipelineTaskNames = new HashSet<>();

        String previous = null;
        for (Task task : tasks) {
            String taskName = task.getMetadata().getName();
            String pipelineTaskName = taskName;
            for (int i = 2; !pipelineTaskNames.add(pipelineTaskName); i++) {
                pipelineTaskName = taskName + "-" + i;
            }

            List<Param> params = new ArrayList<>();
            List<WorkspacePipelineTaskBinding> workspaces = new ArrayList<>();
            if (task.getSpec() != null) {
                for (ParamSpec spec : listOf(task.getSpec().getParams())) {
                    String type = spec.getType() != null ? spec.getType() : "string";
                    String paramName = spec.getName();
                    ParamSpec existing = pipelineParams.get(paramName);
                    if (existing != null && !type.equals(existing.getType())) {
                        paramName = pipelineTaskName + "-" + spec.getName();
                    }
                    pipelineParams.putIfAbsent(paramName, new ParamSpecBuilder(spec).withName(paramName).withType(type)
                            .build());
                    params.add(new ParamBuilder().withName(spec.getName()).withValue(reference(paramName, type)).build());
                }

                for (WorkspaceDeclaration ws : listOf(task.getSpec().getWorkspaces())) {
                    boolean optional = Boolean.TRUE.equals(ws.getOptional());
                    pipelineWorkspaces.merge(ws.getName(),
                            new PipelineWorkspaceDeclarationBuilder().withName(ws.getName())
                                    .withDescription(ws.getDescription()).withOptional(optional ? true : null).build(),
                            (a, b) -> new PipelineWorkspaceDeclarationBuilder(a)
                                    .withOptional(
                                            Boolean.TRUE.equals(a.getOptional()) && Boolean.TRUE.equals(b.getOptional()) ? true
                                                    : null)
                                    .build());
                    workspaces.add(new WorkspacePipelineTaskBindingBuilder().withName(ws.getName())
                            .withWorkspace(ws.getName()).build());
                }
            }

            PipelineTaskBuilder pipelineTask = new PipelineTaskBuilder()
                    .withName(pipelineTaskName)
                    .withNewTaskRef().withName(taskName).endTaskRef()
                    .withParams(params)
                    .withWorkspaces(workspaces);
            if (previous != null) {
                pipelineTask.withRunAfter(previous);
            }
            pipelineTasks.add(pipelineTask.build());
            previous = pipelineTaskName;
        }

        return new PipelineBuilder()
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .withNewSpec()
                .withParams(new ArrayList<>(pipelineParams.values()))
                .withWorkspaces(new ArrayList<>(pipelineWorkspaces.values()))
                .withTasks(pipelineTasks)
                .endSpec()
                .build();
    }

    private static ParamValue reference(String paramName, String type) {
        return switch (type) {
            case "array" -> new ParamValue(List.of("$(params." + paramName + "[*])"));
            case "object" -> new ParamValue("$(params." + paramName + "[*])");
            default -> new ParamValue("$(params." + paramName + ")");
        };
    }

    private static <T> List<T> listOf(List<T> list) {
        return list != null ? list : List.of();
    }
}
