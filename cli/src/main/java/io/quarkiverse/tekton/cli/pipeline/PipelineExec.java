package io.quarkiverse.tekton.cli.pipeline;

import java.nio.file.Path;
import java.util.*;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.tekton.v1.Param;
import io.fabric8.tekton.v1.PipelineRun;
import io.fabric8.tekton.v1.PipelineRunBuilder;
import io.fabric8.tekton.v1.WorkspaceBinding;
import io.quarkiverse.tekton.cli.common.Clients;
import io.quarkiverse.tekton.cli.common.PipelineRuns;
import io.quarkiverse.tekton.cli.common.WorkspaceBindings;
import io.quarkiverse.tekton.common.utils.Params;
import io.quarkiverse.tekton.common.utils.Projects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Unmatched;

@Command(name = "exec", header = "Execute Tekton pipelines.")
public class PipelineExec extends AbstractPipelineCommand {

    @Parameters(index = "0", paramLabel = "PIPELINE", description = "Pipeline name.")
    String pipelineName;

    @Option(names = { "-r", "--regenerate" }, description = "Regenerate and reinstall the pipeline.")
    boolean regenerate = false;

    @Unmatched
    private List<String> pipelineArgs = new ArrayList<>();

    @Override
    public boolean shouldOverwrite() {
        return regenerate;
    }

    @Override
    public void process(List<HasMetadata> resources) {
        readInstalledPipelines();
        readProjectPipelines(resources);
        WorkspaceBindings.readBindingResources(resources);
        Path projectRootDirPath = Projects.getProjectRoot();
        String projectName = Projects.getArtifactId(projectRootDirPath);
        if (regenerate) {
            getProjectPipeline(pipelineName).ifPresentOrElse(t -> {
                Clients.kubernetes().resource(t).serverSideApply();
                if (t instanceof io.fabric8.tekton.v1beta1.Pipeline v1beta1Pipeline) {
                    addInstalledPipeline(v1beta1Pipeline);
                }
                if (t instanceof io.fabric8.tekton.v1.Pipeline v1Pipeline) {
                    addInstalledPipeline(v1Pipeline);
                }
            }, () -> {
                throw new IllegalArgumentException("Failed to regenerate/reinstall Pipeline " + pipelineName + ".");
            });
        }

        if (!isInstalled(pipelineName)) {
            if (isProject(pipelineName)) {
                output.err().println("Pipeline " + pipelineName
                        + " not installed, but its available in the project. You can retry after installing it.");
            } else {
                output.err().println("Pipeline " + pipelineName + " not installed.");
            }
            return;
        } else {
            output.out().println("Executing Pipeline: " + pipelineName);
            HasMetadata t = getInstalledPipeline(pipelineName).get();

            List<WorkspaceBinding> workspaceBindings = new ArrayList<>();
            Map<String, String> parameters = new HashMap<>();

            if (t instanceof io.fabric8.tekton.v1beta1.Pipeline v1beta1Pipeline) {
                v1beta1Pipeline.getSpec().getWorkspaces().forEach(w -> {
                    String workspaceName = w.getName();

                    WorkspaceBindings.forName(projectName, workspaceName)
                            .or(() -> !Boolean.TRUE.equals(w.getOptional())
                                    ? WorkspaceBindings.forEmpty(projectName, workspaceName)
                                    : Optional.empty())
                            .ifPresent(workspaceBindings::add);
                });
                v1beta1Pipeline.getSpec().getParams().forEach(p -> parameters.put(p.getName(), p.getType()));
            } else if (t instanceof io.fabric8.tekton.v1.Pipeline v1Pipeline) {
                v1Pipeline.getSpec().getWorkspaces().forEach(w -> {
                    String workspaceName = w.getName();

                    WorkspaceBindings.forName(projectName, workspaceName)
                            .or(() -> !Boolean.TRUE.equals(w.getOptional())
                                    ? WorkspaceBindings.forEmpty(projectName, workspaceName)
                                    : Optional.empty())
                            .ifPresent(workspaceBindings::add);
                });
                v1Pipeline.getSpec().getParams().forEach(p -> parameters.put(p.getName(), p.getType()));
            }

            List<Param> params = parameters.size() == 1
                    ? Params.createSingle(parameters.keySet().iterator().next(), parameters.values().iterator().next(),
                            pipelineArgs)
                    : Params.create(pipelineArgs);

            for (WorkspaceBinding binding : workspaceBindings) {
                WorkspaceBindings.createIfNeeded(binding);
            }

            String pipelineRunName = pipelineName + "-run";
            PipelineRun pipelineRun = new PipelineRunBuilder()
                    .withNewMetadata()
                    .withName(pipelineRunName)
                    .endMetadata()
                    .withNewSpec()
                    .withNewPipelineRef()
                    .withName(t.getMetadata().getName())
                    .endPipelineRef()
                    .withWorkspaces(workspaceBindings)
                    .withParams(params)
                    .endSpec()
                    .build();

            if (Clients.kubernetes().resource(pipelineRun).get() != null) {
                Clients.kubernetes().resource(pipelineRun).delete();
            }
            pipelineRun = Clients.kubernetes().resource(pipelineRun).serverSideApply();
            output.out().printf("Created PipelineRun %s.\n", pipelineRunName);
            PipelineRuns.waitUntilReady(pipelineRunName);
            PipelineRuns.log(pipelineRunName);
            return;
        }
    }
}
