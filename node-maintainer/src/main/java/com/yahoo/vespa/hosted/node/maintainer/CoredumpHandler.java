// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.maintainer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
import java.util.stream.Collectors;

/**
 * Finds coredumps, collects metadata and reports them
 *
 * @author freva
 */
class CoredumpHandler {

    static final String PROCESSING_DIRECTORY_NAME = "processing";
    static final String METADATA_FILE_NAME = "metadata.json";

    private final Logger logger = Logger.getLogger(CoredumpHandler.class.getName());
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient;
    private final CoreCollector coreCollector;
    private final Path coredumpsPath;
    private final Path doneCoredumpsPath;
    private final Map<String, Object> nodeAttributes;
    private final Optional<Path> installStatePath;
    private final String feedEndpoint;

    public CoredumpHandler(HttpClient httpClient, CoreCollector coreCollector, Path coredumpsPath, Path doneCoredumpsPath,
                           Map<String, Object> nodeAttributes, Optional<Path> installStatePath, String feedEndpoint) {
        this.httpClient = httpClient;
        this.coreCollector = coreCollector;
        this.coredumpsPath = coredumpsPath;
        this.doneCoredumpsPath = doneCoredumpsPath;
        this.nodeAttributes = nodeAttributes;
        this.installStatePath = installStatePath;
        this.feedEndpoint = feedEndpoint;
    }

    public void processAll() throws IOException {
        removeJavaCoredumps();
        handleNewCoredumps();
        removeOldCoredumps();
    }

    private void removeJavaCoredumps() throws IOException {
        if (! coredumpsPath.toFile().isDirectory()) return;
        FileHelper.deleteFiles(coredumpsPath, Duration.ZERO, Optional.of("^java_pid.*\\.hprof$"), false);
    }

    private void removeOldCoredumps() throws IOException {
        if (! doneCoredumpsPath.toFile().isDirectory()) return;
        FileHelper.deleteDirectories(doneCoredumpsPath, Duration.ofDays(10), Optional.empty());
    }

    private void handleNewCoredumps() {
        Path processingCoredumps = enqueueCoredumps();
        processAndReportCoredumps(processingCoredumps);
    }


    /**
     * Moves a coredump to a new directory under the processing/ directory. Limit to only processing
     * one coredump at the time, starting with the oldest.
     */
    Path enqueueCoredumps() {
        Path processingCoredumpsPath = coredumpsPath.resolve(PROCESSING_DIRECTORY_NAME);
        processingCoredumpsPath.toFile().mkdirs();
        if (!FileHelper.listContentsOfDirectory(processingCoredumpsPath).isEmpty()) return processingCoredumpsPath;

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

        return processingCoredumpsPath;
    }

    void processAndReportCoredumps(Path processingCoredumpsPath) {
        doneCoredumpsPath.toFile().mkdirs();

        FileHelper.listContentsOfDirectory(processingCoredumpsPath).stream()
                .filter(path -> path.toFile().isDirectory())
                .forEach(coredumpDirectory -> {
                    try {
                        String metadata = collectMetadata(coredumpDirectory, nodeAttributes);
                        report(coredumpDirectory, metadata);
                        finishProcessing(coredumpDirectory);
                    } catch (Throwable e) {
                        logger.log(Level.WARNING, "Failed to report coredump " + coredumpDirectory, e);
                    }
                });
    }

    Path enqueueCoredumpForProcessing(Path coredumpPath, Path processingCoredumpsPath) throws IOException {
        // Make coredump readable
        coredumpPath.toFile().setReadable(true, false);

        // Create new directory for this coredump and move it into it
        Path folder = processingCoredumpsPath.resolve(UUID.randomUUID().toString());
        folder.toFile().mkdirs();
        return Files.move(coredumpPath, folder.resolve(coredumpPath.getFileName()));
    }

    String collectMetadata(Path coredumpDirectory, Map<String, Object> nodeAttributes) throws IOException {
        Path metadataPath = coredumpDirectory.resolve(METADATA_FILE_NAME);
        if (!Files.exists(metadataPath)) {
            Path coredumpPath = FileHelper.listContentsOfDirectory(coredumpDirectory).stream().findFirst()
                    .orElseThrow(() -> new RuntimeException("No coredump file found in processing directory " + coredumpDirectory));
            Map<String, Object> metadata = coreCollector.collect(coredumpPath, installStatePath);
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

    void report(Path coredumpDirectory, String metadata) throws IOException {
        // Use core dump UUID as document ID
        String documentId = coredumpDirectory.getFileName().toString();

        HttpPost post = new HttpPost(feedEndpoint + "/" + documentId);
        post.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        post.setEntity(new StringEntity(metadata));

        HttpResponse response = httpClient.execute(post);
        if (response.getStatusLine().getStatusCode() / 100 != 2) {
            String result = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))
                    .lines().collect(Collectors.joining("\n"));
            throw new RuntimeException("POST to " + post.getURI() + " failed with HTTP: " +
                    response.getStatusLine().getStatusCode() + " [" + result + "]");
        }
        EntityUtils.consume(response.getEntity());
        logger.info("Successfully reported coredump " + documentId);
    }

    void finishProcessing(Path coredumpDirectory) throws IOException {
        Files.move(coredumpDirectory, doneCoredumpsPath.resolve(coredumpDirectory.getFileName()));
    }

}
