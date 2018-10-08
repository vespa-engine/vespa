// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.coredump;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.hosted.node.admin.task.util.file.FileHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Finds coredumps, collects metadata and reports them
 *
 * @author freva
 */
public class CoredumpHandler {

    private static final Pattern JAVA_COREDUMP_PATTERN = Pattern.compile("java_pid.*\\.hprof");
    private static final String PROCESSING_DIRECTORY_NAME = "processing";
    static final String METADATA_FILE_NAME = "metadata.json";

    private final Logger logger = Logger.getLogger(CoredumpHandler.class.getName());
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final CoreCollector coreCollector;
    private final FileHelper fileHelper;
    private final Path doneCoredumpsPath;
    private final CoredumpReporter coredumpReporter;

    public CoredumpHandler(CoredumpReporter coredumpReporter, Path doneCoredumpsPath) {
        this(new CoreCollector(new ProcessExecuter()), new FileHelper(), coredumpReporter, doneCoredumpsPath);
    }

    CoredumpHandler(CoreCollector coreCollector, FileHelper fileHelper, CoredumpReporter coredumpReporter, Path doneCoredumpsPath) {
        this.fileHelper = fileHelper;
        this.coreCollector = coreCollector;
        this.coredumpReporter = coredumpReporter;
        this.doneCoredumpsPath = doneCoredumpsPath;
    }

    public void processAll(Path coredumpsPath, Map<String, Object> nodeAttributes) {
        fileHelper.streamFiles(coredumpsPath)
                .filterFile(FileHelper.nameMatches(JAVA_COREDUMP_PATTERN))
                .delete();

        enqueueCoredumps(coredumpsPath);
        processAndReportCoredumps(coredumpsPath, nodeAttributes);
    }


    /**
     * Moves a coredump to a new directory under the processing/ directory. Limit to only processing
     * one coredump at the time, starting with the oldest.
     */
    void enqueueCoredumps(Path coredumpsPath) {
        Path processingCoredumpsPath = getProcessingCoredumpsPath(coredumpsPath);
        fileHelper.createDirectories(processingCoredumpsPath);
        if (!fileHelper.streamDirectories(processingCoredumpsPath).list().isEmpty()) return;

        fileHelper.streamFiles(coredumpsPath)
                .filterFile(FileHelper.nameStartsWith(".").negate())
                .stream()
                .min(Comparator.comparing(FileHelper.FileAttributes::lastModifiedTime))
                .map(FileHelper.FileAttributes::path)
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
        fileHelper.createDirectories(doneCoredumpsPath);

        fileHelper.streamDirectories(processingCoredumpsPath)
                .forEachPath(coredumpDirectory -> processAndReportSingleCoredump(coredumpDirectory, nodeAttributes));
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
        // Create new directory for this coredump and move it into it
        Path folder = processingCoredumpsPath.resolve(UUID.randomUUID().toString());
        fileHelper.createDirectories(folder);
        Files.move(coredumpPath, folder.resolve(coredumpPath.getFileName()));
    }

    String collectMetadata(Path coredumpDirectory, Map<String, Object> nodeAttributes) throws IOException {
        Path metadataPath = coredumpDirectory.resolve(METADATA_FILE_NAME);
        if (!Files.exists(metadataPath)) {
            Path coredumpPath = fileHelper.streamFiles(coredumpDirectory)
                    .stream()
                    .map(FileHelper.FileAttributes::path)
                    .findFirst()
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
