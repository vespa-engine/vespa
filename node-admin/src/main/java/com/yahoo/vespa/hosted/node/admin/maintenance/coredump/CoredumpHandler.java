// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.coredump;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.hosted.node.admin.task.util.process.Terminal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder.nameEndsWith;
import static com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder.nameMatches;
import static com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder.nameStartsWith;
import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Finds coredumps, collects metadata and reports them
 *
 * @author freva
 */
public class CoredumpHandler {

    private static final Pattern JAVA_CORE_PATTERN = Pattern.compile("java_pid.*\\.hprof");
    private static final String LZ4_PATH = "/usr/bin/lz4";
    private static final String PROCESSING_DIRECTORY_NAME = "processing";
    private static final String METADATA_FILE_NAME = "metadata.json";
    private static final String COREDUMP_FILENAME_PREFIX = "dump_";

    private final Logger logger = Logger.getLogger(CoredumpHandler.class.getName());
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Terminal terminal;
    private final CoreCollector coreCollector;
    private final CoredumpReporter coredumpReporter;
    private final Path crashPatchInContainer;
    private final Path doneCoredumpsPath;
    private final Supplier<String> coredumpIdSupplier;

    /**
     * @param crashPathInContainer path inside the container where core dump are dumped
     * @param doneCoredumpsPath path on host where processed core dumps are stored
     */
    public CoredumpHandler(Terminal terminal, CoreCollector coreCollector, CoredumpReporter coredumpReporter,
                           Path crashPathInContainer, Path doneCoredumpsPath) {
        this(terminal, coreCollector, coredumpReporter, crashPathInContainer, doneCoredumpsPath, () -> UUID.randomUUID().toString());
    }

    CoredumpHandler(Terminal terminal, CoreCollector coreCollector, CoredumpReporter coredumpReporter,
                           Path crashPathInContainer, Path doneCoredumpsPath, Supplier<String> coredumpIdSupplier) {
        this.terminal = terminal;
        this.coreCollector = coreCollector;
        this.coredumpReporter = coredumpReporter;
        this.crashPatchInContainer = crashPathInContainer;
        this.doneCoredumpsPath = doneCoredumpsPath;
        this.coredumpIdSupplier = coredumpIdSupplier;
    }


    public void converge(NodeAgentContext context, Supplier<Map<String, Object>> nodeAttributesSupplier) {
        Path containerCrashPathOnHost = context.pathOnHostFromPathInNode(crashPatchInContainer);
        Path containerProcessingPathOnHost = containerCrashPathOnHost.resolve(PROCESSING_DIRECTORY_NAME);

        // Remove java core dumps
        FileFinder.files(containerCrashPathOnHost)
                .match(nameMatches(JAVA_CORE_PATTERN))
                .maxDepth(1)
                .deleteRecursively();

        // Check if we have already started to process a core dump or we can enqueue a new core one
        getCoredumpToProcess(containerCrashPathOnHost, containerProcessingPathOnHost)
                .ifPresent(path -> processAndReportSingleCoredump(context, path, nodeAttributesSupplier));
    }

    /** @return path to directory inside processing directory that contains a core dump file to process */
    Optional<Path> getCoredumpToProcess(Path containerCrashPathOnHost, Path containerProcessingPathOnHost) {
        return FileFinder.directories(containerProcessingPathOnHost).stream()
                .map(FileFinder.FileAttributes::path)
                .findAny()
                .or(() -> enqueueCoredump(containerCrashPathOnHost, containerProcessingPathOnHost));
    }

    /**
     * Moves a coredump to a new directory under the processing/ directory. Limit to only processing
     * one coredump at the time, starting with the oldest.
     *
     * @return path to directory inside processing directory which contains the enqueued core dump file
     */
    Optional<Path> enqueueCoredump(Path containerCrashPathOnHost, Path containerProcessingPathOnHost) {
        return FileFinder.files(containerCrashPathOnHost)
                .match(nameStartsWith(".").negate())
                .maxDepth(1)
                .stream()
                .min(Comparator.comparing(FileFinder.FileAttributes::lastModifiedTime))
                .map(FileFinder.FileAttributes::path)
                .map(coredumpPath -> {
                    UnixPath coredumpInProcessingDirectory = new UnixPath(
                            containerProcessingPathOnHost
                                    .resolve(coredumpIdSupplier.get())
                                    .resolve(COREDUMP_FILENAME_PREFIX + coredumpPath.getFileName()));
                    coredumpInProcessingDirectory.createParents();
                    return uncheck(() -> Files.move(coredumpPath, coredumpInProcessingDirectory.toPath())).getParent();
                });
    }

    void processAndReportSingleCoredump(NodeAgentContext context, Path coredumpDirectory, Supplier<Map<String, Object>> nodeAttributesSupplier) {
        try {
            String metadata = getMetadata(context, coredumpDirectory, nodeAttributesSupplier);
            String coredumpId = coredumpDirectory.getFileName().toString();
            coredumpReporter.reportCoredump(coredumpId, metadata);
            finishProcessing(context, coredumpDirectory);
            context.log(logger, "Successfully reported coredump " + coredumpId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process coredump " + coredumpDirectory, e);
        }
    }

    /**
     * @return coredump metadata from metadata.json if present, otherwise attempts to get metadata using
     * {@link CoreCollector} and stores it to metadata.json
     */
    String getMetadata(NodeAgentContext context, Path coredumpDirectory, Supplier<Map<String, Object>> nodeAttributesSupplier) throws IOException {
        UnixPath metadataPath = new UnixPath(coredumpDirectory.resolve(METADATA_FILE_NAME));
        if (!Files.exists(metadataPath.toPath())) {
            Path coredumpFilePathOnHost = findCoredumpFileInProcessingDirectory(coredumpDirectory);
            Path coredumpFilePathInContainer = context.pathInNodeFromPathOnHost(coredumpFilePathOnHost);
            Map<String, Object> metadata = coreCollector.collect(context, coredumpFilePathInContainer);
            metadata.putAll(nodeAttributesSupplier.get());

            String metadataFields = objectMapper.writeValueAsString(ImmutableMap.of("fields", metadata));
            metadataPath.writeUtf8File(metadataFields);
            return metadataFields;
        } else {
            return metadataPath.readUtf8File();
        }
    }

    /**
     * Compresses core file (and deletes the uncompressed core), then moves the entire core dump processing
     * directory to {@link #doneCoredumpsPath} for archive
     */
    private void finishProcessing(NodeAgentContext context, Path coredumpDirectory) throws IOException {
        Path coreFile = findCoredumpFileInProcessingDirectory(coredumpDirectory);
        Path compressedCoreFile = coreFile.getParent().resolve(coreFile.getFileName() + ".lz4");
        terminal.newCommandLine(context)
                .add(LZ4_PATH, "-f", coreFile.toString(), compressedCoreFile.toString())
                .setTimeout(Duration.ofMinutes(30))
                .execute();
        new UnixPath(compressedCoreFile).setPermissions("rw-r-----");
        Files.delete(coreFile);

        Path newCoredumpDirectory = doneCoredumpsPath.resolve(coredumpDirectory.getFileName());
        Files.move(coredumpDirectory, newCoredumpDirectory);
    }

    Path findCoredumpFileInProcessingDirectory(Path coredumpProccessingDirectory) {
        return FileFinder.files(coredumpProccessingDirectory)
                .match(nameStartsWith(COREDUMP_FILENAME_PREFIX).and(nameEndsWith(".lz4").negate()))
                .maxDepth(1)
                .stream()
                .map(FileFinder.FileAttributes::path)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No coredump file found in processing directory " + coredumpProccessingDirectory));
    }
}
