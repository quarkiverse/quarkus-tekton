package io.quarkiverse.tekton.cli.common;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.quarkus.picocli.runtime.PicocliCommandLineFactory;
import picocli.CommandLine;

@ApplicationScoped
public class CommandLineProducer {

    @Produces
    CommandLine customCommandLine(PicocliCommandLineFactory factory) {
        return factory.create()
                .setExecutionExceptionHandler(new ExecutionExceptionHandler());
    }
}
