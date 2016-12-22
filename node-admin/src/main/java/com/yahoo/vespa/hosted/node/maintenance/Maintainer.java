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
import com.yahoo.vespa.hosted.node.admin.util.PathResolver;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;
import io.airlift.airline.Arguments;
import io.airlift.airline.Cli;
import io.airlift.airline.Command;
import io.airlift.airline.Help;
import io.airlift.airline.Option;
import io.airlift.airline.ParseArgumentsUnexpectedException;
import io.airlift.airline.ParseOptionMissingException;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author freva
 */
public class Maintainer {
    private static final Environment environment = new Environment.Builder().pathResolver(new PathResolver()).build();
    private static final Maintainer maintainer = new Maintainer();
    private static final CoredumpHandler COREDUMP_HANDLER =
            new CoredumpHandler(HttpClientBuilder.create().build(), new CoreCollector(maintainer));
    private static final Gson gson = new Gson();

    private static final String JOB_DELETE_OLD_APP_DATA = "delete-old-app-data";
    private static final String JOB_ARCHIVE_APP_DATA = "archive-app-data";
    private static final String JOB_CLEAN_CORE_DUMPS = "clean-core-dumps";
    private static final String JOB_HANDLE_CORE_DUMPS = "handle-core-dumps";

    private static Optional<String> kernelVersion = Optional.empty();

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        LogSetup.initVespaLogging(Maintainer.class.getSimpleName().toLowerCase());

        Cli.CliBuilder<Runnable> builder = Cli.<Runnable>builder("maintainer.jar")
                .withDescription("This tool makes it easy to delete old log files and other node-admin app data.")
                .withDefaultCommand(Help.class)
                .withCommands(Help.class,
                        DeleteOldAppDataArguments.class,
                        CleanCoreDumpsArguments.class,
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

        nodeSpec.wantedDockerImage.ifPresent(image -> attributes.put("docker_image", image.asString()));
        nodeSpec.vespaVersion.ifPresent(version -> attributes.put("vespa_version", version));
        nodeSpec.owner.ifPresent(owner -> {
            attributes.put("tenant", owner.tenant);
            attributes.put("application", owner.application);
            attributes.put("instance", owner.instance);
        });

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

    private static String[] concatenateArrays(String[] ar1, String... ar2) {
        String[] concatenated = new String[ar1.length + ar2.length];
        System.arraycopy(ar1, 0, concatenated, 0, ar1.length);
        System.arraycopy(ar2, 0, concatenated, ar1.length, ar2.length);
        return concatenated;
    }

    @Command(name = JOB_DELETE_OLD_APP_DATA, description = "Deletes old app data")
    public static class DeleteOldAppDataArguments implements Runnable {
        @Override
        public void run() {
            String path = environment.getPathResolver().getApplicationStoragePathForNodeAdmin().toString();
            String regex = "^" + Pattern.quote(Environment.APPLICATION_STORAGE_CLEANUP_PATH_PREFIX);

            DeleteOldAppData.deleteDirectories(path, Duration.ofDays(7).getSeconds(), regex);
        }
    }

    @Command(name = JOB_CLEAN_CORE_DUMPS, description = "Clean core dumps")
    public static class CleanCoreDumpsArguments implements Runnable {
        @Override
        public void run() {
            Path doneCoredumps = environment.pathInNodeAdminToDoneCoredumps();

            if (doneCoredumps.toFile().exists()) {
                COREDUMP_HANDLER.removeOldCoredumps(doneCoredumps);
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
            File yVarDir = environment.pathInNodeAdminFromPathInNode(containerName, "/home/y/var").toFile();
            if (yVarDir.exists()) {
                logger.info("Recursively deleting " + yVarDir);
                try {
                    DeleteOldAppData.recursiveDelete(yVarDir);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to delete " + yVarDir, e);
                }
            }

            Path from = environment.pathInNodeAdminFromPathInNode(containerName, "/");
            if (!Files.exists(from)) {
                logger.info("The container storage at " + from + " doesn't exist");
                return;
            }

            Path to = environment.pathInNodeAdminToNodeCleanup(containerName);
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

                Path path = environment.pathInNodeAdminFromPathInNode(new ContainerName(container), "/home/y/var/crash");
                Path doneCoredumps = environment.pathInNodeAdminToDoneCoredumps();

                COREDUMP_HANDLER.removeJavaCoredumps(path);
                COREDUMP_HANDLER.processAndReportCoredumps(path, doneCoredumps, attributesMap);
            } catch (Throwable e) {
                logger.log(Level.WARNING, "Could not process coredumps", e);
            }
        }
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
