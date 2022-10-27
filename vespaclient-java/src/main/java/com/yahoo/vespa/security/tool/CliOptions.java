// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author vekterli
 * @author bjorncs
 */
class CliOptions {

    private static final Option HELP_OPTION = Option.builder("h")
            .longOpt("help")
            .hasArg(false)
            .required(false)
            .desc("Show help")
            .build();

    static Options withHelpOption(List<Option> options) {
        var optionsWithHelp = new Options();
        options.forEach(optionsWithHelp::addOption);
        optionsWithHelp.addOption(HELP_OPTION);
        return optionsWithHelp;
    }

    static void printTopLevelHelp(PrintStream out, List<Tool> tools) {
        var formatter = new HelpFormatter();
        var writer    = new PrintWriter(out);
        formatter.printHelp(
                writer,
                formatter.getWidth(),
                "vespa-security <tool> [TOOL OPTIONS]",
                "Where <tool> is one of: %s".formatted(tools.stream().map(Tool::name).collect(Collectors.joining(", "))),
                withHelpOption(List.of()),
                formatter.getLeftPadding(),
                formatter.getDescPadding(),
                "Invoke vespa-security <tool> --help for tool-specific help");
        writer.flush();
    }

    static void printToolSpecificHelp(PrintStream out, String toolName,
                                      ToolDescription toolDesc,
                                      Options optionsWithHelp) {
        var formatter = new HelpFormatter();
        var writer    = new PrintWriter(out);
        formatter.printHelp(
                writer,
                formatter.getWidth(),
                "vespa-security %s %s".formatted(toolName, toolDesc.helpArgSuffix()),
                toolDesc.helpHeader(),
                optionsWithHelp,
                formatter.getLeftPadding(),
                formatter.getDescPadding(),
                toolDesc.helpFooter());
        writer.flush();
    }
}

