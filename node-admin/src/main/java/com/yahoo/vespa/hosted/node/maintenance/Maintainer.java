// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.maintenance;

import com.google.gson.Gson;
import com.yahoo.io.IOUtils;
import com.yahoo.log.LogSetup;
import com.yahoo.net.HostName;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.ProcessResult;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import io.airlift.airline.Arguments;
import io.airlift.airline.Cli;
import io.airlift.airline.Command;
import io.airlift.airline.Help;
import io.airlift.airline.Option;
import io.airlift.airline.ParseArgumentsUnexpectedException;
import io.airlift.airline.ParseOptionMissingException;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author freva
 */
public class Maintainer {
    private static final Path ROOT = Paths.get("/");
    private static final Path RELATIVE_APPLICATION_STORAGE_PATH = Paths.get("home/docker/container-storage");
    private static final Path APPLICATION_STORAGE_PATH_FOR_NODE_ADMIN = Paths.get("/host").resolve(RELATIVE_APPLICATION_STORAGE_PATH);
    private static final Path APPLICATION_STORAGE_PATH_FOR_HOST = ROOT.resolve(RELATIVE_APPLICATION_STORAGE_PATH);
    private static final String APPLICATION_STORAGE_CLEANUP_PATH_PREFIX = "cleanup_";

    private static final Maintainer maintainer = new Maintainer();
    private static final HttpClient HTTP_CLIENT = HttpClientBuilder.create().build();
    private static final CoreCollector CORE_COLLECTOR = new CoreCollector(maintainer);
    private static final Gson gson = new Gson();

    private static DateFormat filenameFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private static final String JOB_DELETE_OLD_APP_DATA = "delete-old-app-data";
    private static final String JOB_ARCHIVE_APP_DATA = "archive-app-data";
    private static final String JOB_CLEAN_CORE_DUMPS = "clean-core-dumps";
    private static final String JOB_CLEAN_HOME = "clean-home";
    private static final String JOB_HANDLE_CORE_DUMPS = "handle-core-dumps";

    private static Optional<String> kernelVersion = Optional.empty();

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
                        ArchiveApplicationData.class,
                        HandleCoreDumpsForContainer.class);

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

    public static void handleCoreDumpsForContainer(PrefixLogger logger, ContainerNodeSpec nodeSpec, Environment environment) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("hostname", nodeSpec.hostname);
        attributes.put("parent_hostname", HostName.getLocalhost());
        attributes.put("region", environment.getRegion());
        attributes.put("environment", environment.getEnvironment());
        attributes.put("flavor", nodeSpec.nodeFlavor);
        try {
            attributes.put("kernel_version", getKernelVersion());
        } catch (Throwable ignored) {
            attributes.put("kernel_version", "unknown");
        }

        if (nodeSpec.wantedDockerImage.isPresent()) attributes.put("docker_image", nodeSpec.wantedDockerImage.get().asString());
        if (nodeSpec.vespaVersion.isPresent()) attributes.put("vespa_version", nodeSpec.vespaVersion.get());
        if (nodeSpec.owner.isPresent()) {
            attributes.put("tenant", nodeSpec.owner.get().tenant);
            attributes.put("application", nodeSpec.owner.get().application);
            attributes.put("instance", nodeSpec.owner.get().instance);
        }

        executeMaintainer(logger, JOB_HANDLE_CORE_DUMPS,
                "--container", nodeSpec.containerName.asString(),
                "--attributes", gson.toJson(attributes));
    }

    private static void executeMaintainer(PrefixLogger logger, String... params) {
        String[] baseArguments = {"sudo", "/home/y/libexec/vespa/node-admin/maintenance.sh"};
        String[] args = concatenateArrays(baseArguments, params);
        ProcessBuilder processBuilder = new ProcessBuilder(args);
        Map<String, String> env = processBuilder.environment();
        env.put("VESPA_SERVICE_NAME", "maintainer");

        try {
            ProcessResult result = maintainer.exec(args);

            if (! result.getOutput().isEmpty()) logger.info(result.getOutput());
            if (! result.getErrors().isEmpty()) logger.error(result.getErrors());
        } catch (IOException | InterruptedException e) {
            logger.warning("Failed to execute command " + Arrays.toString(args), e);
        }
    }

    public ProcessResult exec(String... args) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(args);
        Process process = processBuilder.start();
        String output = IOUtils.readAll(new InputStreamReader(process.getInputStream()));
        String errors = IOUtils.readAll(new InputStreamReader(process.getErrorStream()));

        return new ProcessResult(process.waitFor(), output, errors);
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
            Path doneCoredumps = maintainer.pathInNodeAdminToDoneCoredumps();

            if (doneCoredumps.toFile().exists()) {
                CoredumpHandler.removeOldCoredumps(doneCoredumps);
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

    @SuppressWarnings("unchecked")
    @Command(name = JOB_HANDLE_CORE_DUMPS, description = "Finds container's coredumps, collects metadata and reports them")
    public static class HandleCoreDumpsForContainer implements Runnable {
        @Option(name = "--container", description = "Name of the container")
        public String container;

        @Option(name = "--attributes", description = "Comma separated key=value pairs")
        public String attributes;

        @Override
        public void run() {
            Logger logger = Logger.getLogger(HandleCoreDumpsForContainer.class.getName());

            if (container == null) {
                throw new IllegalArgumentException("<container> is required");
            }

            try {
                Map<String, Object> attributesMap = (Map<String, Object>) gson.fromJson(attributes, Map.class);

                Path path = maintainer.pathInNodeAdminFromPathInNode(new ContainerName(container), "/home/y/var/crash");
                Path doneCoredumps = maintainer.pathInNodeAdminToDoneCoredumps();

                CoredumpHandler coredumpHandler = new CoredumpHandler(HTTP_CLIENT, CORE_COLLECTOR, attributesMap);
                CoredumpHandler.removeJavaCoredumps(path);
                coredumpHandler.processAndReportCoredumps(path, doneCoredumps);
            } catch (Throwable e) {
                logger.log(Level.WARNING, "Could not process coredumps", e);
            }
        }
    }


    /**
     * Absolute path in node admin to directory with processed and reported core dumps
     */
    private Path pathInNodeAdminToDoneCoredumps() {
        return APPLICATION_STORAGE_PATH_FOR_NODE_ADMIN.resolve("processed-coredumps");
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

    public static String getKernelVersion() throws IOException, InterruptedException {
        if (! kernelVersion.isPresent()) {
            ProcessResult result = maintainer.exec("uname", "-r");
            if (result.isSuccess()) {
                kernelVersion = Optional.of(result.getOutput().trim());
            } else {
                throw new RuntimeException("Failed to get kernel version\n" + result);
            }
        }

        return kernelVersion.get();
    }
}
