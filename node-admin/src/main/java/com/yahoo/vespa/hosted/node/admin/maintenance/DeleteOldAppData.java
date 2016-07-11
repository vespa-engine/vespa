package com.yahoo.vespa.hosted.node.admin.maintenance;

import io.airlift.command.Command;
import io.airlift.command.HelpOption;
import io.airlift.command.Option;
import io.airlift.command.SingleCommand;

import javax.inject.Inject;
import java.io.File;
import java.time.Duration;

/**
 * @author valerijf
 */
@Command(name = "delete-old-app-data",
        description = "This is a tool for deleting old app data.")
public class DeleteOldAppData {
    public static void main(String[] args) {
        final DeleteOldAppData cmdArgs;
        try {
            cmdArgs = SingleCommand.singleCommand(DeleteOldAppData.class).parse(args);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.err.println("Use --help to show usage.\n");
            return;
        }
        if (cmdArgs.helpOption.showHelpIfRequested()) {
            return;
        }

        if (cmdArgs.path == null) {
            System.err.println("'--path' not set.");
            return;
        }

        deleteOldAppData(cmdArgs.path, cmdArgs.maxAge, cmdArgs.prefix, cmdArgs.suffix);
    }


    public static void deleteOldAppData(String path, long maxAgeSeconds, String prefix, String suffix) {
        File deleteFolder = new File(path);
        File[] filesInDeleteFolder = deleteFolder.listFiles();

        if (filesInDeleteFolder == null) {
            throw new IllegalArgumentException("The specified path is not a folder");
        }

        int numFilesDeleted = 0;
        int numFilesFailedToDelete = 0;
        for (File file : filesInDeleteFolder) {
            if ((prefix == null || file.getName().startsWith(prefix)) && (suffix == null || file.getName().endsWith(suffix))) {
                if (file.lastModified() + maxAgeSeconds*1000 < System.currentTimeMillis()) {
                    if (file.isDirectory()) {
                        System.err.println("Skipped: " + file.getAbsolutePath() + " is a directory");
                    } else if (!file.delete()) {
                        System.err.println("Could not delete file: " + file.getAbsolutePath());
                        numFilesFailedToDelete++;
                    } else {
                        numFilesDeleted++;
                    }
                }
            }
        }

        System.out.println("Deleted " + numFilesDeleted + " out of " + filesInDeleteFolder.length + " with " +
                numFilesFailedToDelete + " failures.");
    }


    @Inject
    private HelpOption helpOption;


    @Option(name = {"--path"},
            description = "Path to directory which contains the app data")
    private String path;

    @Option(name = {"--max_age"},
            description = "Delete files older than (in seconds)")
    private long maxAge = Duration.ofDays(7).getSeconds();

    @Option(name = {"--prefix"},
            description = "Delete files that start with prefix")
    private String prefix;

    @Option(name = {"--suffix"},
            description = "Delete files that end with suffix")
    private String suffix;
}
