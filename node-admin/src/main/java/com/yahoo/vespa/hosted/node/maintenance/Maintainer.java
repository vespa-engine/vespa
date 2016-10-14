// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.maintenance;

import com.yahoo.io.IOUtils;
import com.yahoo.log.LogSetup;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import io.airlift.airline.Arguments;
import io.airlift.airline.Cli;
import io.airlift.airline.Command;
import io.airlift.airline.Help;
import io.airlift.airline.ParseArgumentsUnexpectedException;
import io.airlift.airline.ParseOptionMissingException;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author valerijf
 */
public class Maintainer {
    private static final Path ROOT = Paths.get("/");
    private static final Path RELATIVE_APPLICATION_STORAGE_PATH = Paths.get("home/docker/container-storage");
    private static final Path APPLICATION_STORAGE_PATH_FOR_NODE_ADMIN = Paths.get("/host").resolve(RELATIVE_APPLICATION_STORAGE_PATH);
    private static final Path APPLICATION_STORAGE_PATH_FOR_HOST = ROOT.resolve(RELATIVE_APPLICATION_STORAGE_PATH);
    private static final String APPLICATION_STORAGE_CLEANUP_PATH_PREFIX = "cleanup_";

    private static DateFormat filenameFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

    public static final String JOB_DELETE_OLD_APP_DATA = "delete-old-app-data";
    public static final String JOB_ARCHIVE_APP_DATA = "archive-app-data";
    public static final String JOB_CLEAN_CORE_DUMPS = "clean-core-dumps";
    public static final String JOB_CLEAN_HOME = "clean-home";

    static {
        filenameFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        LogSetup.initVespaLogging(Maintainer.class.getSimpleName().toLowerCase());

        Cli.CliBuilder<Runnable> builder = Cli.<Runnable>builder("maintainer.jar")
                .withDescription("This tool makes it easy to delete old log files and other node-admin app data.")
                .withDefaultCommand(Help.class)
                .withCommands(Help.class,
                        DeleteOldAppDataArguments.class,
                        CleanCoreDumpsArguments.class,
                        CleanHomeArguments.class,
                        ArchiveApplicationData.class);

        Cli<Runnable> gitParser = builder.build();
        try {
            gitParser.parse(args).run();
        } catch (ParseArgumentsUnexpectedException | ParseOptionMissingException e) {
            System.err.println(e.getMessage());
            gitParser.parse("help").run();
        }
    }

    public static void cleanCoreDumps(PrefixLogger logger) {
        executeMaintainer(logger, JOB_CLEAN_CORE_DUMPS);
    }

    public static void deleteOldAppData(PrefixLogger logger) {
        executeMaintainer(logger, JOB_DELETE_OLD_APP_DATA);
    }

    public static void cleanHome(PrefixLogger logger) {
        executeMaintainer(logger, JOB_CLEAN_HOME);
    }

    public static void archiveAppData(PrefixLogger logger, ContainerName containerName) {
        executeMaintainer(logger, JOB_ARCHIVE_APP_DATA, containerName.asString());
    }

    private static void executeMaintainer(PrefixLogger logger, String... params) {
        String[] baseArguments = {"sudo", "/home/y/libexec/vespa/node-admin/maintenance.sh"};
        String[] args = concatenateArrays(baseArguments, params);
        ProcessBuilder processBuilder = new ProcessBuilder(args);
        Map<String, String> env = processBuilder.environment();
        env.put("VESPA_SERVICE_NAME", "maintainer");

        try {
            Process process = processBuilder.start();
            String output = IOUtils.readAll(new InputStreamReader(process.getInputStream()));
            String errors = IOUtils.readAll(new InputStreamReader(process.getErrorStream()));

            if (! output.isEmpty()) logger.info(output);
            if (! errors.isEmpty()) logger.error(errors);
        } catch (IOException e) {
            logger.warning("Failed to execute command " + Arrays.toString(args), e);
        }
    }

    public static String[] concatenateArrays(String[] ar1, String... ar2) {
        String[] concatenated = new String[ar1.length + ar2.length];
        System.arraycopy(ar1, 0, concatenated, 0, ar1.length);
        System.arraycopy(ar2, 0, concatenated, ar1.length, ar2.length);
        return concatenated;
    }

    @Command(name = JOB_DELETE_OLD_APP_DATA, description = "Deletes old app data")
    public static class DeleteOldAppDataArguments implements Runnable {
        @Override
        public void run() {
            String path = APPLICATION_STORAGE_PATH_FOR_NODE_ADMIN.toString();
            String regex = "^" + Pattern.quote(APPLICATION_STORAGE_CLEANUP_PATH_PREFIX);

            DeleteOldAppData.deleteDirectories(path, Duration.ofDays(7).getSeconds(), regex);
        }
    }

    @Command(name = JOB_CLEAN_CORE_DUMPS, description = "Clean core dumps")
    public static class CleanCoreDumpsArguments implements Runnable {
        @Override
        public void run() {
            File coreDumpsDir = new File("/home/y/var/crash");

            if (coreDumpsDir.exists()) {
                DeleteOldAppData.deleteFilesExceptNMostRecent(coreDumpsDir.getAbsolutePath(), 1);
            }
        }
    }

    @Command(name = JOB_CLEAN_HOME, description = "Clean home directories for large files")
    public static class CleanHomeArguments implements Runnable {
        @Override
        public void run() {
            List<String> exceptions = Arrays.asList("docker", "y", "yahoo");
            File homeDir = new File("/home/");
            long MB = 1 << 20;

            if (homeDir.exists() && homeDir.isDirectory()) {
                for (File file : homeDir.listFiles()) {
                    if (! exceptions.contains(file.getName())) {
                        DeleteOldAppData.deleteFilesLargerThan(file, 100*MB);
                    }
                }
            }
        }
    }

    @Command(name = JOB_ARCHIVE_APP_DATA, description = "Move container's container-storage to cleanup")
    public static class ArchiveApplicationData implements Runnable {
        @Arguments(description = "Name of container to archive (required)")
        public String container;

        @Override
        public void run() {
            if (container == null) {
                throw new IllegalArgumentException("<container> is required");
            }
            // Note that ContainerName verifies the name, so it cannot
            // contain / or be equal to "." or "..".
            ContainerName containerName = new ContainerName(container);

            Logger logger = Logger.getLogger(ArchiveApplicationData.class.getName());
            Maintainer maintainer = new Maintainer();

            File yVarDir = maintainer.pathInNodeAdminFromPathInNode(containerName, "/home/y/var").toFile();
            if (yVarDir.exists()) {
                logger.info("Recursively deleting " + yVarDir);
                try {
                    DeleteOldAppData.recursiveDelete(yVarDir);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to delete " + yVarDir, e);
                }
            }

            Path from = maintainer.pathInNodeAdminFromPathInNode(containerName, "/");
            if (!Files.exists(from)) {
                logger.info("The container storage at " + from + " doesn't exist");
                return;
            }

            Path to = maintainer.pathInNodeAdminToNodeCleanup(containerName);
            logger.info("Moving container storage from " + from + " to " + to);
            try {
                Files.move(from, to);
            } catch (IOException e) {
                throw new RuntimeException("Failed to move " + from + " to " + to, e);
            }
        }
    }


    /**
     * Absolute path in node admin container to the node cleanup directory.
     */
    public Path pathInNodeAdminToNodeCleanup(ContainerName containerName) {
        return APPLICATION_STORAGE_PATH_FOR_NODE_ADMIN.resolve(APPLICATION_STORAGE_CLEANUP_PATH_PREFIX +
                containerName.asString() + "_" + filenameFormatter.format(Date.from(Instant.now())));
    }

    /**
     * Translates an absolute path in node agent container to an absolute path in node admin container.
     * @param containerName name of the node agent container
     * @param absolutePathInNode absolute path in that container
     * @return the absolute path in node admin container pointing at the same inode
     */
    public Path pathInNodeAdminFromPathInNode(ContainerName containerName, String absolutePathInNode) {
        Path pathInNode = Paths.get(absolutePathInNode);
        if (! pathInNode.isAbsolute()) {
            throw new IllegalArgumentException("The specified path in node was not absolute: " + absolutePathInNode);
        }

        return APPLICATION_STORAGE_PATH_FOR_NODE_ADMIN
                .resolve(containerName.asString())
                .resolve(ROOT.relativize(pathInNode));
    }

    /**
     * Translates an absolute path in node agent container to an absolute path in host.
     * @param containerName name of the node agent container
     * @param absolutePathInNode absolute path in that container
     * @return the absolute path in host pointing at the same inode
     */
    public Path pathInHostFromPathInNode(ContainerName containerName, String absolutePathInNode) {
        Path pathInNode = Paths.get(absolutePathInNode);
        if (! pathInNode.isAbsolute()) {
            throw new IllegalArgumentException("The specified path in node was not absolute: " + absolutePathInNode);
        }

        return APPLICATION_STORAGE_PATH_FOR_HOST
                .resolve(containerName.asString())
                .resolve(ROOT.relativize(pathInNode));
    }
}
