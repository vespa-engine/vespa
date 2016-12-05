// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.maintenance;

import com.google.gson.Gson;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Finds coredumps, collects metadata and reports them
 *
 * @author freva
 */
public class CoredumpHandler {
    public static final String FEED_ENDPOINT = "http://panic.vespa.us-west-1.prod.vespa.yahooapis.com:4080/document/v1/panic/core_dump/docid";
    public static final String PROCESSING_DIRECTORY_NAME = "processing";
    public static final String METADATA_FILE_NAME = "metadata.json";

    private static final Logger logger = Logger.getLogger(CoredumpHandler.class.getName());
    private static final Gson gson = new Gson();

    private final HttpClient httpClient;
    private final CoreCollector coreCollector;

    private final Map<String, Object> nodeAttributes;

    public CoredumpHandler(HttpClient httpClient, CoreCollector coreCollector, Map<String, Object> nodeAttributes) {
        this.httpClient = httpClient;
        this.coreCollector = coreCollector;
        this.nodeAttributes = nodeAttributes;
    }

    public void processAndReportCoredumps(Path coredumpsPath, Path doneCoredumpPath) throws IOException {
        Path processingCoredumps = processCoredumps(coredumpsPath);
        reportCoredumps(processingCoredumps, doneCoredumpPath);
    }

    public static void removeJavaCoredumps(Path javaCoredumpsPath) {
        DeleteOldAppData.deleteFiles(javaCoredumpsPath.toString(), 0, "^java_pid.*\\.hprof$", false);
    }

    Path processCoredumps(Path coredumpsPath) throws IOException {
        Path processingCoredumpsPath = coredumpsPath.resolve(PROCESSING_DIRECTORY_NAME);
        processingCoredumpsPath.toFile().mkdirs();

        Files.list(coredumpsPath)
                .filter(path -> path.toFile().isFile() && ! path.getFileName().toString().startsWith("."))
                .forEach(coredumpPath -> {
                    try {
                        coredumpPath = startProcessing(coredumpPath, processingCoredumpsPath);

                        Path metadataPath = coredumpPath.getParent().resolve(METADATA_FILE_NAME);
                        Map<String, Object> metadata = collectMetadata(coredumpPath);
                        writeMetadata(metadataPath, metadata);
                    } catch (Throwable e) {
                        logger.log(Level.WARNING, "Failed to process coredump " + coredumpPath, e);
                    }
                });

        return processingCoredumpsPath;
    }

    void reportCoredumps(Path processingCoredumpsPath, Path doneCoredumpsPath) throws IOException {
        doneCoredumpsPath.toFile().mkdirs();

        Files.list(processingCoredumpsPath)
                .filter(path -> path.toFile().isDirectory())
                .forEach(coredumpDirectory -> {
                    try {
                        report(coredumpDirectory);
                        finishProcessing(coredumpDirectory, doneCoredumpsPath);
                    } catch (Throwable e) {
                        logger.log(Level.WARNING, "Failed to report coredump " + coredumpDirectory, e);
                    }
                });
    }

    public static void removeOldCoredumps(Path doneCoredumpsPath) {
        DeleteOldAppData.deleteDirectories(doneCoredumpsPath.toString(), Duration.ofDays(10).getSeconds(), null);
    }

    private static Path startProcessing(Path coredumpPath, Path processingCoredumpsPath) throws IOException {
        Path folder = processingCoredumpsPath.resolve(UUID.randomUUID().toString());
        folder.toFile().mkdirs();
        return Files.move(coredumpPath, folder.resolve(coredumpPath.getFileName()));
    }

    private Map<String, Object> collectMetadata(Path coredumpPath) throws IOException, InterruptedException {
        Map<String, Object> metadata = coreCollector.collect(coredumpPath);
        metadata.putAll(nodeAttributes);

        Map<String, Object> fields = new HashMap<>();
        fields.put("fields", metadata);
        return fields;
    }

    private void writeMetadata(Path metadataPath, Map<String, Object> metadata) throws IOException {
        Files.write(metadataPath, gson.toJson(metadata).getBytes());
    }

    private void report(Path coredumpDirectory) throws IOException, InterruptedException {
        // Use core dump UUID as document ID
        String documentId = coredumpDirectory.getFileName().toString();
        String metadata = new String(Files.readAllBytes(coredumpDirectory.resolve(METADATA_FILE_NAME)));

        HttpPost post = new HttpPost(FEED_ENDPOINT + "/" + documentId);
        post.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        post.setEntity(new StringEntity(metadata));

        HttpResponse response = httpClient.execute(post);
        if (response.getStatusLine().getStatusCode() / 100 != 2) {
            String result = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))
                    .lines().collect(Collectors.joining("\n"));
            throw new RuntimeException("POST to " + post.getURI() + " failed with HTTP: " +
                    response.getStatusLine().getStatusCode() + " [" + result + "]");
        }
        logger.info("Successfully reported coredump " + documentId);
    }

    private static void finishProcessing(Path coredumpDirectory, Path doneCoredumpsPath) throws IOException {
        Files.move(coredumpDirectory, doneCoredumpsPath.resolve(coredumpDirectory.getFileName()));
    }
}
