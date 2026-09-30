package io.quarkiverse.tekton.cli.common;

import io.quarkiverse.tekton.cli.TektonCommand;
import picocli.CommandLine;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;

/**
 * Prints a user-friendly error message instead of the full stacktrace.
 * The stacktrace is only printed when the `-e` / `--errors` option is used.
 */
public class ExecutionExceptionHandler implements IExecutionExceptionHandler {

    @Override
    public int handleExecutionException(Exception ex, CommandLine commandLine, ParseResult parseResult) {
        CommandLine root = commandLine;
        while (root.getParent() != null) {
            root = root.getParent();
        }

        if (root.getCommand() instanceof TektonCommand tektonCommand && tektonCommand.getOutput() != null) {
            OutputOptionMixin output = tektonCommand.getOutput();
            if (isShowErrors(commandLine)) {
                output.showErrors = true;
            }
            return output.handleCommandException(ex, getMessage(ex));
        }

        commandLine.getErr().println(commandLine.getColorScheme().errorText(getMessage(ex)));
        return commandLine.getCommandSpec().exitCodeOnExecutionException();
    }

    private static boolean isShowErrors(CommandLine commandLine) {
        for (CommandLine cmd = commandLine; cmd != null; cmd = cmd.getParent()) {
            ParseResult parseResult = cmd.getParseResult();
            if (parseResult != null && parseResult.hasMatchedOption("--errors")) {
                return true;
            }
        }
        return false;
    }

    private static String getMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        return message + " (use --errors to see the full stacktrace)";
    }
}
