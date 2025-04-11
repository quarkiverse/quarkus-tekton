package io.quarkiverse.tekton.cli.task;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.tekton.v1.Param;
import io.fabric8.tekton.v1.TaskRun;
import io.fabric8.tekton.v1.TaskRunBuilder;
import io.fabric8.tekton.v1.WorkspaceBinding;
import io.quarkiverse.tekton.cli.common.TaskRuns;
import io.quarkiverse.tekton.common.utils.Clients;
import io.quarkiverse.tekton.common.utils.Params;
import io.quarkiverse.tekton.common.utils.Projects;
import io.quarkiverse.tekton.common.utils.WorkspaceBindings;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Unmatched;

@Command(name = "exec", header = "Execute Tekton task.")
public class TaskExec extends AbstractTaskCommand {

    @Parameters(index = "0", paramLabel = "TASK", description = "Task name.")
    String taskName;

    @Option(names = { "-r", "--regenerate" }, description = "Regenerate and reinstall the task.")
    boolean regenerate = false;

    @Unmatched
    private List<String> taskArgs = new ArrayList<>();

    @Override
    public boolean shouldOverwrite() {
        return regenerate;
    }

    @Override
    public void process(List<HasMetadata> resources) {
        readInstalledTasks();
        readProjectTasks(resources);
        WorkspaceBindings.readBindingResources(resources);
        Path projectRootDirPath = Projects.getProjectRoot();
        String projectName = Projects.getArtifactId(projectRootDirPath);
        if (regenerate) {
            getProjectTask(taskName).ifPresentOrElse(t -> {
                Clients.kubernetes().resource(t).serverSideApply();
                if (t instanceof io.fabric8.tekton.v1beta1.Task v1beta1Task) {
                    addInstalledTask(v1beta1Task);
                }
                if (t instanceof io.fabric8.tekton.v1.Task v1Task) {
                    addInstalledTask(v1Task);
                }
            }, () -> {
                throw new IllegalArgumentException("Failed to regenerate/reinstall Task " + taskName + ".");
            });
        }

        if (!isInstalled(taskName)) {
            if (isProject(taskName)) {
                output.err().println("Task " + taskName
                        + " not installed, but its available in the project. You can retry after installing it.");
            } else {
                output.err().println("Task " + taskName + " not installed.");
            }
            return;
        } else {
            output.out().println("Executing Task: " + taskName);
            HasMetadata t = getInstalledTask(taskName).get();

            List<WorkspaceBinding> workspaceBindings = new ArrayList<>();
            Map<String, String> parameters = new HashMap<>();

            if (t instanceof io.fabric8.tekton.v1beta1.Task v1beta1Task) {
                v1beta1Task.getSpec().getWorkspaces().forEach(w -> {
                    String workspaceName = w.getName();

                    WorkspaceBindings.forName(projectName, workspaceName)
                            .or(() -> !Boolean.TRUE.equals(w.getOptional())
                                    ? WorkspaceBindings.forEmpty(projectName, workspaceName)
                                    : Optional.empty())
                            .ifPresent(workspaceBindings::add);
                });
                v1beta1Task.getSpec().getParams().forEach(p -> parameters.put(p.getName(), p.getType()));
            } else if (t instanceof io.fabric8.tekton.v1.Task v1Task) {
                v1Task.getSpec().getWorkspaces().forEach(w -> {
                    String workspaceName = w.getName();

                    WorkspaceBindings.forName(projectName, workspaceName)
                            .or(() -> !Boolean.TRUE.equals(w.getOptional())
                                    ? WorkspaceBindings.forEmpty(projectName, workspaceName)
                                    : Optional.empty())
                            .ifPresent(workspaceBindings::add);
                });
                v1Task.getSpec().getParams().forEach(p -> parameters.put(p.getName(), p.getType()));
            }

            // Convert the arguments passed by the Cli command as List<String> into a Map<String,String> where the key is equal is the left part of key=val
            List<Param> params = Params.create(taskArgs.stream()
                    .map(s -> s.split("=", 2)) // Split each string into at most two parts
                    .filter(parts -> parts.length == 2) // Ensure we have both key and value
                    .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1])));

            for (WorkspaceBinding binding : workspaceBindings) {
                WorkspaceBindings.createIfNeeded(binding);
            }

            String taskRunName = taskName + "-run";
            TaskRun taskRun = new TaskRunBuilder()
                    .withNewMetadata()
                    .withName(taskRunName)
                    .endMetadata()
                    .withNewSpec()
                    .withNewTaskRef()
                    .withName(t.getMetadata().getName())
                    .endTaskRef()
                    .withWorkspaces(workspaceBindings)
                    .withParams(params)
                    .endSpec()
                    .build();

            if (Clients.kubernetes().resource(taskRun).get() != null) {
                Clients.kubernetes().resource(taskRun).delete();
            }
            taskRun = Clients.kubernetes().resource(taskRun).serverSideApply();
            output.out().printf("Created TaskRun %s.\n", taskRunName);
            TaskRuns.waitUntilReady(taskRunName);
            TaskRuns.log(taskRunName);
            return;
        }
    }
}
