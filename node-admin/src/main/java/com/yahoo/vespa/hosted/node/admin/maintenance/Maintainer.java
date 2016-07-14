package com.yahoo.vespa.hosted.node.admin.maintenance;

import io.airlift.airline.Cli;
import io.airlift.airline.Command;
import io.airlift.airline.Help;
import io.airlift.airline.Option;
import io.airlift.airline.ParseArgumentsUnexpectedException;
import io.airlift.airline.ParseOptionMissingException;

/**
 * @author valerijf
 */
public class Maintainer {
    @SuppressWarnings("unchecked")
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


    @Command(name = "delete-old-app-data", description = "Deletes all directories and their contents that matches the criteria")
    public static class DeleteOldAppDataArguments implements Runnable {
        @Option(name = {"--path"},
                required = true,
                description = "Path to directory which contains the app data")
        private String path;

        @Option(name = {"--max_age"},
                description = "Delete directories older than (in seconds)")
        private long maxAge = DeleteOldAppData.DEFAULT_MAX_AGE_IN_SECONDS;

        @Option(name = {"--prefix"},
                description = "Delete directories that start with prefix")
        private String prefix;

        @Option(name = {"--suffix"},
                description = "Delete directories that end with suffix")
        private String suffix;

        @Override
        public void run() {
            DeleteOldAppData.deleteDirectories(path, maxAge, prefix, suffix);
        }
    }

    @Command(name = "delete-old-logs", description = "Deletes all log files that match the criteria in path")
    public static class DeleteOldLogsArguments implements Runnable {
        @Option(name = {"--path"},
                required = true,
                description = "Path to directory which contains the log data")
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
            DeleteOldAppData.deleteFiles(path, maxAge, prefix, suffix, false);
        }
    }
}
