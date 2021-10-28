// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool.securityenv;

import com.yahoo.security.tls.TransportSecurityUtils;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Arrays;

import static java.util.stream.Collectors.joining;

/**
 * Defines the program's command line parameters.
 *
 * @author bjorncs
 */
class CliOptions {
    static final String SHELL_OPTION = "shell";
    static final String HELP_OPTION = "help";

    private static final Options OPTIONS = new Options()
            .addOption(
                    Option.builder("s")
                            .longOpt(SHELL_OPTION)
                            .hasArg(true)
                            .required(false)
                            .desc(String.format("Shell type. Shell type is auto-detected if option not present. Valid values: %s.",
                                                Arrays.stream(UnixShell.values())
                                                        .map(shell -> String.format("'%s'", shell.configName()))
                                                        .collect(joining(", ", "[", "]"))))
                            .build())
            .addOption(Option.builder("h")
                               .longOpt(HELP_OPTION)
                               .hasArg(false)
                               .required(false)
                               .desc("Show help")
                               .build());

    static CommandLine parseCliArguments(String[] cliArgs) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        return parser.parse(OPTIONS, cliArgs);
    }

    static void printHelp(PrintStream out) {
        HelpFormatter formatter = new HelpFormatter();
        PrintWriter writer = new PrintWriter(out);
        formatter.printHelp(
                writer,
                formatter.getWidth(),
                "vespa-security-env <options>",
                String.format("Generates shell commands that defines environments variables based on the content of %s.",
                              TransportSecurityUtils.CONFIG_FILE_ENVIRONMENT_VARIABLE),
                OPTIONS,
                formatter.getLeftPadding(),
                formatter.getDescPadding(),
                String.format("The output may include the following variables:\n%s\n",
                              Arrays.stream(OutputVariable.values())
                                      .map(variable -> String.format(" - '%s': %s", variable.variableName(), variable.description()))
                                      .collect(joining("\n"))));
        writer.flush();
    }
}
