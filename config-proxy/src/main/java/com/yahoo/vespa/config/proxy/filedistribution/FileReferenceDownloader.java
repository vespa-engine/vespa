//  Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.FileReference;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.StringValue;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.Connection;
import com.yahoo.vespa.config.ConnectionPool;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Downloads file reference using rpc requests to config server and keeps track of files being downloaded
 * <p>
 * Some methods are synchronized to make sure access to downloads is atomic
 *
 * @author hmusum
 */
// TODO: Add retries when a config server does not have a file reference
// TODO: Handle shutdown of executors
class FileReferenceDownloader {

    private final static Logger log = Logger.getLogger(FileReferenceDownloader.class.getName());
    private final static Duration rpcTimeout = Duration.ofSeconds(10);

    private final File downloadDirectory;
    private final ExecutorService downloadExecutor =
            Executors.newFixedThreadPool(10, new DaemonThreadFactory("filereference downloader"));
    private ExecutorService readFromQueueExecutor =
            Executors.newFixedThreadPool(1, new DaemonThreadFactory("filereference download queue"));
    private final ConnectionPool connectionPool;
    private final ConcurrentLinkedQueue<FileReferenceDownload> downloadQueue = new ConcurrentLinkedQueue<>();
    private final Map<FileReference, FileReferenceDownload> downloads = new LinkedHashMap<>();
    private final Map<FileReference, Double> downloadStatus = new HashMap<>();
    private final Duration downloadTimeout;

    FileReferenceDownloader(File downloadDirectory, ConnectionPool connectionPool, Duration timeout) {
        this.downloadDirectory = downloadDirectory;
        this.connectionPool = connectionPool;
        this.downloadTimeout = timeout;
        readFromQueueExecutor.submit(this::readFromQueue);
    }

    private synchronized Optional<File> startDownload(FileReference fileReference,
                                                      Duration timeout,
                                                      FileReferenceDownload fileReferenceDownload)
            throws ExecutionException, InterruptedException, TimeoutException {
        downloads.put(fileReference, fileReferenceDownload);
        setDownloadStatus(fileReference.value(), 0.0);
        if (startDownloadRpc(fileReference))
            return fileReferenceDownload.future().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        else {
            fileReferenceDownload.future().setException(new RuntimeException("Failed getting file"));
            downloads.remove(fileReference);
            return Optional.empty();
        }
    }

    synchronized void addToDownloadQueue(FileReferenceDownload fileReferenceDownload) {
        downloadQueue.add(fileReferenceDownload);
    }

    void receiveFile(FileReference fileReference, String filename, byte[] content) {
        File fileReferenceDir = new File(downloadDirectory, fileReference.value());
        try {
            Files.createDirectories(fileReferenceDir.toPath());
            File file = new File(fileReferenceDir, filename);
            log.log(LogLevel.INFO, "Writing data to " + file.getAbsolutePath());
            Files.write(file.toPath(), content);
            completedDownloading(fileReference, file);
        } catch (IOException e) {
            log.log(LogLevel.ERROR, "Failed writing file: " + e.getMessage());
            throw new RuntimeException("Failed writing file: ", e);
        }
    }

    synchronized Set<FileReference> queuedForDownload() {
        return downloadQueue.stream()
                .map(FileReferenceDownload::fileReference)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void readFromQueue() {
        do {
            FileReferenceDownload fileReferenceDownload = downloadQueue.poll();
            if (fileReferenceDownload == null) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) { /* ignore for now */}
            } else {
                log.log(LogLevel.INFO, "Polling queue, found file reference '" +
                        fileReferenceDownload.fileReference().value() + "' to download");
                downloadExecutor.submit(() -> startDownload(fileReferenceDownload.fileReference(), downloadTimeout, fileReferenceDownload));
            }
        } while (true);
    }

    private synchronized void completedDownloading(FileReference fileReference, File file) {
        downloads.get(fileReference).future().set(Optional.of(file));
        downloadStatus.put(fileReference, 100.0);
    }

    private boolean startDownloadRpc(FileReference fileReference) throws ExecutionException, InterruptedException {
        Connection connection = connectionPool.getCurrent();
        Request request = new Request("filedistribution.serveFile");
        request.parameters().add(new StringValue(fileReference.value()));

        execute(request, connection);
        if (validateResponse(request)) {
            log.log(LogLevel.DEBUG, "Request callback, OK. Req: " + request + "\nSpec: " + connection);
            if (request.returnValues().get(0).asInt32() == 0)
                log.log(LogLevel.INFO, "Found file reference '" + fileReference.value() + "' available at " + connection.getAddress());
            else
                log.log(LogLevel.INFO, "File reference '" + fileReference.value() + "' not found for " + connection.getAddress());
            return true;
        } else {
            log.log(LogLevel.WARNING, "Request failed. Req: " + request + "\nSpec: " + connection.getAddress());
            connection.setError(request.errorCode());
            // TODO: Retry with another config server
            return false;
        }
    }

    synchronized boolean isDownloading(FileReference fileReference) {
        return downloads.containsKey(fileReference);
    }

    synchronized ListenableFuture<Optional<File>> addDownloadListener(FileReference fileReference, Runnable runnable) {
        FileReferenceDownload fileReferenceDownload = downloads.get(fileReference);
        fileReferenceDownload.future().addListener(runnable, downloadExecutor);
        return fileReferenceDownload.future();
    }

    private void execute(Request request, Connection connection) {
        connection.invokeSync(request, (double) rpcTimeout.getSeconds());
    }

    private boolean validateResponse(Request request) {
        if (request.isError()) {
            return false;
        } else if (request.returnValues().size() == 0) {
            return false;
        } else if (!request.checkReturnTypes("i")) {
            log.log(LogLevel.WARNING, "Invalid return types for response: " + request.errorMessage());
            return false;
        }
        return true;
    }

    double downloadStatus(String file) {
        return downloadStatus.getOrDefault(new FileReference(file), 0.0);
    }

    void setDownloadStatus(String file, double percentageDownloaded) {
        downloadStatus.put(new FileReference(file), percentageDownloaded);
    }

    Map<FileReference, Double> downloadStatus() {
        return ImmutableMap.copyOf(downloadStatus);
    }
}
