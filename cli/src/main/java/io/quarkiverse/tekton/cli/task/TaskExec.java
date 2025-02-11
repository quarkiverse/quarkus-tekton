package io.quarkiverse.tekton.cli.task;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.tekton.v1.Param;
import io.fabric8.tekton.v1.TaskRun;
import io.fabric8.tekton.v1.TaskRunBuilder;
import io.fabric8.tekton.v1.WorkspaceBinding;
import io.quarkiverse.tekton.cli.common.Clients;
import io.quarkiverse.tekton.cli.common.TaskRuns;
import io.quarkiverse.tekton.cli.common.WorkspaceBindings;
import io.quarkiverse.tekton.common.utils.Params;
import io.quarkiverse.tekton.common.utils.Projects;
import io.quarkiverse.tekton.common.utils.Serialization;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "exec", header = "Execute tekton tasks.")
public class TaskExec extends AbstractTaskCommand {

    @Parameters(index = "0", paramLabel = "TASK", description = "Task name.")
    String taskName;

    @Parameters(index = "1..", description = "Additional parameters passed to the build system")
    private List<String> taskArgs = new ArrayList<>();

    @Override
    public boolean shouldOverwrite() {
        return false;
    }

    @Override
    public void process(List<HasMetadata> resources) {
        readInstalledTasks();
        readProjectTasks(resources);
        WorkspaceBindings.readBindingResources(resources);
        Path projectRootDirPath = Projects.getProjectRoot();
        String projectName = Projects.getArtifactId(projectRootDirPath);

        if (!isInstalled(taskName)) {
            if (isProject(taskName)) {
                output.err().println("Task " + taskName
                        + " not installed, but its available in the project. You can retry after installing it.");
            } else {
                output.err().println("Task " + taskName + " not installed.");
            }
            return;
        }

        if (isInstalled(taskName)) {
            HasMetadata t = getInstalledTask(taskName).get();

            Map<String, PersistentVolumeClaim> pvcClaims = Clients.kubernetes().persistentVolumeClaims().list().getItems()
                    .stream().collect(Collectors.toMap(p -> p.getMetadata().getName(), Function.identity()));
            Map<String, ConfigMap> configMaps = Clients.kubernetes().configMaps().list().getItems().stream()
                    .collect(Collectors.toMap(c -> c.getMetadata().getName(), Function.identity()));
            Map<String, Secret> secrets = Clients.kubernetes().secrets().list().getItems().stream()
                    .collect(Collectors.toMap(s -> s.getMetadata().getName(), Function.identity()));

            List<WorkspaceBinding> workspaceBindings = new ArrayList<>();
            List<String> parameterNames = new ArrayList<>();

            if (t instanceof io.fabric8.tekton.v1beta1.Task v1beta1Task) {
                v1beta1Task.getSpec().getWorkspaces().forEach(w -> {
                    String workspaceName = w.getName();
                    System.out.println("Searching workspace for v1beta1 Task with name:" + workspaceName);
                    WorkspaceBindings.forName(projectName, workspaceName).ifPresent(workspaceBindings::add);
                });
                v1beta1Task.getSpec().getParams().forEach(p -> parameterNames.add(p.getName()));
            } else if (t instanceof io.fabric8.tekton.v1.Task v1Task) {
                v1Task.getSpec().getWorkspaces().forEach(w -> {
                    String workspaceName = w.getName();
                    System.out.println("Searching workspace for v1 Task with name:" + workspaceName);
                    WorkspaceBindings.forName(projectName, workspaceName).ifPresent(workspaceBindings::add);
                });
                v1Task.getSpec().getParams().forEach(p -> parameterNames.add(p.getName()));
            }

            List<Param> params = parameterNames.size() == 1 ? Params.createSingle(parameterNames.get(0), taskArgs)
                    : Params.create(taskArgs);

            for (Param param : params) {
                output.out().println("Param:\t" + Serialization.asYaml(param));
            }

            for (WorkspaceBinding binding : workspaceBindings) {
                output.out().println("Binding:\t" + Serialization.asYaml(binding));
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
            output.out().printf("Created TaskRun %s.", taskRunName);
            output.out().println(Serialization.asYaml(taskRun));
            TaskRuns.waitUntilReady(taskRunName);
            TaskRuns.log(taskRunName);
            return;
        }
    }
}
