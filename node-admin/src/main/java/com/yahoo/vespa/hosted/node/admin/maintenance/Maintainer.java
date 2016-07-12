package com.yahoo.vespa.hosted.node.admin.maintenance;

import io.airlift.command.Cli;
import io.airlift.command.Command;
import io.airlift.command.Help;
import io.airlift.command.Option;
import io.airlift.command.ParseArgumentsUnexpectedException;
import io.airlift.command.ParseOptionMissingException;

/**
 * @author valerijf
 */
public class Maintainer {
    public static void main(String[] args) {
        Cli.CliBuilder<Runnable> builder = Cli.<Runnable>builder("maintainer.jar")
                .withDescription("This tool makes it easy to delete old log files and other node-admin app data.")
                .withDefaultCommand(Help.class)
                .withCommands(Help.class, DeleteOldAppDataArguments.class, DeleteOldLogsArguments.class);

        Cli<Runnable> gitParser = builder.build();
        try {
            gitParser.parse(args).run();
        } catch (ParseArgumentsUnexpectedException | ParseOptionMissingException e) {
            System.err.println(e.getMessage());
            gitParser.parse("help").run();
        }
    }


    @Command(name = "delete-old-app-data", description = "Deletes all data within a folder and its sub-folders which matches the criteria")
    private static class DeleteOldAppDataArguments implements Runnable {
        @Option(name = {"--path"},
                required = true,
                description = "Path to directory which contains the app data")
        private String path;

        @Option(name = {"--max_age"},
                description = "Delete files older than (in seconds)")
        private long maxAge = DeleteOldAppData.DEFAULT_MAX_AGE_IN_SECONDS;

        @Option(name = {"--prefix"},
                description = "Delete files that start with prefix")
        private String prefix;

        @Option(name = {"--suffix"},
                description = "Delete files that end with suffix")
        private String suffix;

        @Override
        public void run() {
            DeleteOldAppData.deleteOldAppData(path, maxAge, prefix, suffix, true);
        }
    }

    @Command(name = "delete-old-logs", description = "Deletes all log files that match the criteria in path")
    private static class DeleteOldLogsArguments implements Runnable {
        @Option(name = {"--path"},
                required = true,
                description = "Path to directory which contains the app data")
        private String path;

        @Option(name = {"--max_age"},
                description = "Delete files older than (in seconds)")
        private long maxAge = DeleteOldAppData.DEFAULT_MAX_AGE_IN_SECONDS;

        @Option(name = {"--prefix"},
                description = "Delete files that start with prefix")
        private String prefix;

        @Option(name = {"--suffix"},
                description = "Delete files that end with suffix")
        private String suffix;

        @Override
        public void run() {
            DeleteOldAppData.deleteOldAppData(path, maxAge, prefix, suffix, false);
        }
    }
}
