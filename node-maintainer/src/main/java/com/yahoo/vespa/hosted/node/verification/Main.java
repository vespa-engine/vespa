// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.log.LogLevel;
import com.yahoo.log.LogSetup;
import com.yahoo.vespa.hosted.node.verification.commons.CommandExecutor;
import com.yahoo.vespa.hosted.node.verification.commons.report.HardwareDivergenceReport;
import com.yahoo.vespa.hosted.node.verification.hardware.HardwareBenchmarker;
import com.yahoo.vespa.hosted.node.verification.spec.SpecVerifier;
import io.airlift.command.Cli;
import io.airlift.command.Help;
import io.airlift.command.Option;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author freva
 */
public class Main {
    private static final String EXECUTABLE_NAME = "node-verifier";
    private static final Logger logger = Logger.getLogger(Main.class.getName());
    private static final ObjectMapper om = new ObjectMapper();

    public static void main(String[] args) {
        LogSetup.initVespaLogging(EXECUTABLE_NAME);

        try {
            System.out.println(execute(args, new CommandExecutor()));
        } catch (Exception e) {
            logger.log(LogLevel.ERROR, "Something went wrong", e);
            System.exit(1);
        }
    }

    @SuppressWarnings("unchecked")
    public static String execute(String[] args, CommandExecutor commandExecutor) throws IOException {
        Cli.CliBuilder<Object> builder = Cli.builder(EXECUTABLE_NAME)
                .withDescription("Verifies that node meets the expected specification and benchmarks")
                .withDefaultCommand(Help.class)
                .withCommands(Help.class, SpecVerifier.class, HardwareBenchmarker.class);

        Object command = builder.build().parse(args);
        if (command instanceof VerifierCommand) {
            HardwareDivergenceReport report = ((VerifierCommand) command).getPreviousHardwareDivergence();
            ((VerifierCommand) command).run(report, commandExecutor);
            return hardwareDivergenceReportToString(report);
        } else if (command instanceof Runnable) {
            ((Runnable) command).run();
            return "";
        }

        throw new RuntimeException("Unknown command class " + command.getClass().getName());
    }

    public static abstract class VerifierCommand {
        @Option(name = {"-h", "--divergence"}, description = "JSON of the previous hardware divergence report")
        private String hardwareDivergence;

        private HardwareDivergenceReport getPreviousHardwareDivergence() {
            if (hardwareDivergence == null) {
                return new HardwareDivergenceReport();
            }
            try {
                return om.readValue(hardwareDivergence, HardwareDivergenceReport.class);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to parse hardware divergence:\n" + hardwareDivergence, e.getMessage());
                return new HardwareDivergenceReport();
            }
        }

        protected abstract void run(HardwareDivergenceReport hardwareDivergenceReport, CommandExecutor commandExecutor);
    }

    private static String hardwareDivergenceReportToString(HardwareDivergenceReport hardwareDivergenceReport) throws IOException {
        if (hardwareDivergenceReport.isHardwareDivergenceReportEmpty()) {
            return "null";
        } else {
            return om.writeValueAsString(hardwareDivergenceReport);
        }
    }
}
