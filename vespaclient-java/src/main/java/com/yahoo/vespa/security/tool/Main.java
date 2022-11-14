// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool;

import com.yahoo.vespa.security.tool.crypto.ConvertBaseTool;
import com.yahoo.vespa.security.tool.crypto.DecryptTool;
import com.yahoo.vespa.security.tool.crypto.EncryptTool;
import com.yahoo.vespa.security.tool.crypto.KeygenTool;
import com.yahoo.vespa.security.tool.crypto.ResealTool;
import com.yahoo.vespa.security.tool.crypto.TokenInfoTool;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Primary application entry point for security utility tools. Handles tool selection,
 * CLI argument parsing and exception printing.
 *
 * Based on previous vespa-security-env tool.
 *
 * @author vekterli
 * @author bjorncs
 */
public class Main {

    private final InputStream stdIn;
    private final PrintStream stdOut;
    private final PrintStream stdError;

    Main(InputStream stdIn, PrintStream stdOut, PrintStream stdError) {
        this.stdIn = stdIn;
        this.stdOut = stdOut;
        this.stdError = stdError;
    }

    public static void main(String[] args) {
        var program = new Main(System.in, System.out, System.err);
        int returnCode = program.execute(args, System.getenv());
        System.exit(returnCode);
    }

    private static final List<Tool> TOOLS = List.of(
            new KeygenTool(), new EncryptTool(), new DecryptTool(), new TokenInfoTool(),
            new ConvertBaseTool(), new ResealTool());

    private static Optional<Tool> toolFromCliArgs(String[] args) {
        if (args.length == 0) {
            return Optional.empty();
        }
        String toolName = args[0];
        return TOOLS.stream().filter(t -> t.name().equals(toolName)).findFirst();
    }

    private static String[] withToolNameArgRemoved(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Argument array did not contain a tool name");
        }
        String[] truncatedArgs = new String[args.length - 1];
        System.arraycopy(args, 1, truncatedArgs, 0, truncatedArgs.length);
        return truncatedArgs;
    }

    private static CommandLine parseCliArguments(String[] cliArgs, Options options) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        return parser.parse(options, cliArgs);
    }

    public int execute(String[] args, Map<String, String> envVars) {
        boolean debugMode = envVars.containsKey("VESPA_DEBUG");
        try {
            var maybeTool = toolFromCliArgs(args);
            if (maybeTool.isEmpty()) { // This also implicitly covers the top-level --help case.
                CliOptions.printTopLevelHelp(stdOut, TOOLS);
                return 0;
            }
            var tool     = maybeTool.get();
            var toolDesc = tool.description();
            var cliOpts  = CliOptions.withHelpOption(toolDesc.cliOptions());
            String[] truncatedArgs = withToolNameArgRemoved(args);
            var cmdLine = parseCliArguments(truncatedArgs, cliOpts);
            if (cmdLine.hasOption("help")) {
                CliOptions.printToolSpecificHelp(stdOut, tool.name(), toolDesc, cliOpts);
                return 0;
            }
            var invocation = new ToolInvocation(cmdLine, envVars, stdIn, stdOut, stdError, debugMode);
            return tool.invoke(invocation);
        } catch (ParseException e) {
            return handleException("Failed to parse command line arguments: " + e.getMessage(), e, debugMode);
        } catch (IllegalArgumentException e) {
            return handleException("Invalid command line arguments: " + e.getMessage(), e, debugMode);
        } catch (Exception e) {
            return handleException("Got unhandled exception: " + e.getMessage(), e, debugMode);
        }
    }

    private int handleException(String message, Exception exception, boolean debugMode) {
        stdError.println(message);
        if (debugMode) {
            exception.printStackTrace(stdError);
        }
        return 1;
    }

}
