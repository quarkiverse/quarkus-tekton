package io.quarkiverse.tekton.it;

import io.fabric8.tekton.v1.*;

public class TektonResourceGenerator {
    public static Pipeline populateHelloGoodbyePipeline() {
        /*
         * apiVersion: tekton.dev/v1
         * kind: Pipeline
         * metadata:
         * name: hello
         * spec:
         * params:
         * - name: username
         * type: string
         * tasks:
         * - name: hello
         * taskRef:
         * name: hello
         * - name: goodbye
         * runAfter:
         * - hello
         * taskRef:
         * name: goodbye
         * params:
         * - name: username
         * value: $(params.username)
         */
        // @formatter:off
        return new PipelineBuilder()
            .withNewMetadata()
              .withName("hello-goodbye")
            .endMetadata()
            .withNewSpec()
              .withParams(new ParamSpecBuilder()
                  .withName("username")
                  .withType("string")
                  .build())
            .withTasks(
                new PipelineTaskBuilder()
                    .withName("hello")
                    .withTaskRef(new TaskRefBuilder()
                        .withName("hello")
                        .build())
                    .build(),
                new PipelineTaskBuilder()
                    .withName("goodbye")
                    .withRunAfter("hello")
                    .withTaskRef(new TaskRefBuilder()
                        .withName("goodbye")
                        .build())
                    .withParams(new ParamBuilder()
                        .withName("username")
                        .withValue(new ParamValue("$(params.username)"))
                        .build())
                    .build()

            )
            .endSpec()
            .build();
        // @formatter:on
    }

    public static Task populateGoodbyeTask() {
        /*
         * apiVersion: tekton.dev/v1beta1
         * kind: Task
         * metadata:
         * name: goodbye
         * spec:
         * params:
         * - name: username
         * type: string
         * steps:
         * - name: goodbye
         * image: ubuntu
         * script: |
         * #!/bin/bash
         * echo "Goodbye $(params.username)!"
         */
        // @formatter:off
        return new TaskBuilder()
            .withNewMetadata().withName("goodbye")
            .endMetadata()
            .withNewSpec()
              .withParams(new ParamSpecBuilder()
                .withName("username")
                .withType("string")
                .build())
              .withSteps(new StepBuilder()
                  .withName("goodbye")
                  .withImage("ubuntu")
                  .withScript("|" +
                     "#!/bin/bash" +
                     "echo \"Goodbye $(params.username)!\"")
                  .build())
            .endSpec()
            .build();
        // @formatter:on
    }

    public static Task populateHelloTask() {
        /*
         * apiVersion: tekton.dev/v1
         * kind: Task
         * metadata:
         * name: hello
         * spec:
         * steps:
         * - name: echo
         * image: alpine
         * script: |
         * #!/bin/sh
         * echo "Hello World"
         */
        // @formatter:off
        return new TaskBuilder()
            .withNewMetadata().withName("hello")
            .endMetadata()
            .withNewSpec()
            .withSteps(new StepBuilder()
                .withName("echo")
                .withImage("alpine")
                .withScript("|" +
                    "#!/bin/bash" +
                    "echo \"Hello Worl\"")
                .build())
            .endSpec()
            .build();
        // @formatter:on
    }

    public static PipelineRun populatePipelineRun() {
        /*
         * apiVersion: tekton.dev/v1
         * kind: PipelineRun
         * metadata:
         * name: hello-goodbye-run
         * spec:
         * pipelineRef:
         * name: hello-goodbye
         * params:
         * - name: username
         * value: "Tekton"
         */
        // @formatter:off
        return new PipelineRunBuilder()
            .withNewMetadata()
              .withName("hello-goodbye-run")
            .endMetadata()
            .withNewSpec()
            .withPipelineRef(new PipelineRefBuilder().withName("hello-goodbye").build())
            .withParams(
                new ParamBuilder().withName("username").withValue(new ParamValue("tekton")).build()
            )
            .endSpec()
            .build();
        // @formatter:on
    }
}
