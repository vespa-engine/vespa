// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.coredump;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.hosted.node.admin.maintenance.FileHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Finds coredumps, collects metadata and reports them
 *
 * @author freva
 */
public class CoredumpHandler {

    private static final String PROCESSING_DIRECTORY_NAME = "processing";
    static final String METADATA_FILE_NAME = "metadata.json";

    private final Logger logger = Logger.getLogger(CoredumpHandler.class.getName());
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final CoreCollector coreCollector;
    private final Path doneCoredumpsPath;
    private final CoredumpReporter coredumpReporter;

    public CoredumpHandler(CoredumpReporter coredumpReporter, Path doneCoredumpsPath) {
        this(new CoreCollector(new ProcessExecuter()), coredumpReporter, doneCoredumpsPath);
    }

    CoredumpHandler(CoreCollector coreCollector, CoredumpReporter coredumpReporter, Path doneCoredumpsPath) {
        this.coreCollector = coreCollector;
        this.coredumpReporter = coredumpReporter;
        this.doneCoredumpsPath = doneCoredumpsPath;
    }

    public void processAll(Path coredumpsPath, Map<String, Object> nodeAttributes) throws IOException {
        removeJavaCoredumps(coredumpsPath);
        handleNewCoredumps(coredumpsPath, nodeAttributes);
        removeOldCoredumps();
    }

    private void removeJavaCoredumps(Path coredumpsPath) throws IOException {
        if (! coredumpsPath.toFile().isDirectory()) return;
        FileHelper.deleteFiles(coredumpsPath, Duration.ZERO, Optional.of("^java_pid.*\\.hprof$"), false);
    }

    private void removeOldCoredumps() throws IOException {
        if (! doneCoredumpsPath.toFile().isDirectory()) return;
        FileHelper.deleteDirectories(doneCoredumpsPath, Duration.ofDays(10), Optional.empty());
    }

    private void handleNewCoredumps(Path coredumpsPath, Map<String, Object> nodeAttributes) {
        enqueueCoredumps(coredumpsPath);
        processAndReportCoredumps(coredumpsPath, nodeAttributes);
    }


    /**
     * Moves a coredump to a new directory under the processing/ directory. Limit to only processing
     * one coredump at the time, starting with the oldest.
     */
    void enqueueCoredumps(Path coredumpsPath) {
        Path processingCoredumpsPath = getProcessingCoredumpsPath(coredumpsPath);
        processingCoredumpsPath.toFile().mkdirs();
        if (!FileHelper.listContentsOfDirectory(processingCoredumpsPath).isEmpty()) return;

        FileHelper.listContentsOfDirectory(coredumpsPath).stream()
                .filter(path -> path.toFile().isFile() && ! path.getFileName().toString().startsWith("."))
                .min((Comparator.comparingLong(o -> o.toFile().lastModified())))
                .ifPresent(coredumpPath -> {
                    try {
                        enqueueCoredumpForProcessing(coredumpPath, processingCoredumpsPath);
                    } catch (Throwable e) {
                        logger.log(Level.WARNING, "Failed to process coredump " + coredumpPath, e);
                    }
                });
    }

    void processAndReportCoredumps(Path coredumpsPath, Map<String, Object> nodeAttributes) {
        Path processingCoredumpsPath = getProcessingCoredumpsPath(coredumpsPath);
        doneCoredumpsPath.toFile().mkdirs();

        FileHelper.listContentsOfDirectory(processingCoredumpsPath).stream()
                .filter(path -> path.toFile().isDirectory())
                .forEach(coredumpDirectory -> processAndReportSingleCoredump(coredumpDirectory, nodeAttributes));
    }

    private void processAndReportSingleCoredump(Path coredumpDirectory, Map<String, Object> nodeAttributes) {
        try {
            String metadata = collectMetadata(coredumpDirectory, nodeAttributes);
            String coredumpId = coredumpDirectory.getFileName().toString();
            coredumpReporter.reportCoredump(coredumpId, metadata);
            finishProcessing(coredumpDirectory);
            logger.info("Successfully reported coredump " + coredumpId);
        } catch (Throwable e) {
            logger.log(Level.WARNING, "Failed to report coredump " + coredumpDirectory, e);
        }
    }

    private void enqueueCoredumpForProcessing(Path coredumpPath, Path processingCoredumpsPath) throws IOException {
        // Make coredump readable
        coredumpPath.toFile().setReadable(true, false);

        // Create new directory for this coredump and move it into it
        Path folder = processingCoredumpsPath.resolve(UUID.randomUUID().toString());
        folder.toFile().mkdirs();
        Files.move(coredumpPath, folder.resolve(coredumpPath.getFileName()));
    }

    String collectMetadata(Path coredumpDirectory, Map<String, Object> nodeAttributes) throws IOException {
        Path metadataPath = coredumpDirectory.resolve(METADATA_FILE_NAME);
        if (!Files.exists(metadataPath)) {
            Path coredumpPath = FileHelper.listContentsOfDirectory(coredumpDirectory).stream().findFirst()
                    .orElseThrow(() -> new RuntimeException("No coredump file found in processing directory " + coredumpDirectory));
            Map<String, Object> metadata = coreCollector.collect(coredumpPath);
            metadata.putAll(nodeAttributes);

            Map<String, Object> fields = new HashMap<>();
            fields.put("fields", metadata);

            String metadataFields = objectMapper.writeValueAsString(fields);
            Files.write(metadataPath, metadataFields.getBytes());
            return metadataFields;
        } else {
            return new String(Files.readAllBytes(metadataPath));
        }
    }

    private void finishProcessing(Path coredumpDirectory) throws IOException {
        Files.move(coredumpDirectory, doneCoredumpsPath.resolve(coredumpDirectory.getFileName()));
    }

    /**
     * @return Path to directory where coredumps are temporarily moved while still being processed
     */
    static Path getProcessingCoredumpsPath(Path coredumpsPath) {
        return coredumpsPath.resolve(PROCESSING_DIRECTORY_NAME);
    }
}
