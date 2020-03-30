// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.FileReference;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.StringValue;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.Connection;
import com.yahoo.vespa.config.ConnectionPool;

import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Downloads file reference using rpc requests to config server and keeps track of files being downloaded
 * <p>
 * Some methods are synchronized to make sure access to downloads is atomic
 *
 * @author hmusum
 */
public class FileReferenceDownloader {

    private final static Logger log = Logger.getLogger(FileReferenceDownloader.class.getName());
    private final static Duration rpcTimeout = Duration.ofSeconds(10);

    private final ExecutorService downloadExecutor =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new DaemonThreadFactory("filereference downloader"));
    private final ConnectionPool connectionPool;
    private final Map<FileReference, FileReferenceDownload> downloads = new LinkedHashMap<>();
    private final Map<FileReference, Double> downloadStatus = new HashMap<>();  // between 0 and 1
    private final Duration downloadTimeout;
    private final Duration sleepBetweenRetries;

    FileReferenceDownloader(File downloadDirectory, File tmpDirectory, ConnectionPool connectionPool, Duration timeout, Duration sleepBetweenRetries) {
        this.connectionPool = connectionPool;
        this.downloadTimeout = timeout;
        this.sleepBetweenRetries = sleepBetweenRetries;
        // Needed to receive RPC calls receiveFile* from server after asking for files
        new FileReceiver(connectionPool.getSupervisor(), this, downloadDirectory, tmpDirectory);
    }

    private void startDownload(FileReferenceDownload fileReferenceDownload) {
        FileReference fileReference = fileReferenceDownload.fileReference();
        long end = System.currentTimeMillis() + downloadTimeout.toMillis();
        int retryCount = 0;

        log.log(LogLevel.DEBUG, () -> "Will download file reference '" + fileReference.value() + "' with timeout " + downloadTimeout);
        synchronized (downloads) {
            while (System.currentTimeMillis() < end) {
                try {
                    if (startDownloadRpc(fileReferenceDownload, retryCount)) {
                        downloads.put(fileReference, fileReferenceDownload);
                        downloadStatus.put(fileReference, 0.0);
                        return;
                    } else {
                        retryCount++;
                        Thread.sleep(sleepBetweenRetries.toMillis());
                    }
                } catch (InterruptedException e) { /* ignored */}
            }
        }

        fileReferenceDownload.future().setException(
                new RuntimeException("Failed to start download of file reference '" + fileReference.value() + "'"));
    }

    void addToDownloadQueue(FileReferenceDownload fileReferenceDownload) {
        downloadExecutor.submit(() -> startDownload(fileReferenceDownload));
    }

    void completedDownloading(FileReference fileReference, File file) {
        synchronized (downloads) {
            FileReferenceDownload download = downloads.get(fileReference);
            if (download != null) {
                downloadStatus.put(fileReference, 1.0);
                downloads.remove(fileReference);
                download.future().set(Optional.of(file));
            } else {
                log.log(LogLevel.DEBUG, () -> "Received '" + fileReference + "', which was not requested. Can be ignored if happening during upgrades/restarts");
            }
        }
    }

    void failedDownloading(FileReference fileReference) {
        synchronized (downloads) {
            downloadStatus.put(fileReference, 0.0);
            downloads.remove(fileReference);
        }
    }

    private boolean startDownloadRpc(FileReferenceDownload fileReferenceDownload, int retryCount) {
        Connection connection = connectionPool.getCurrent();
        Request request = new Request("filedistribution.serveFile");
        String fileReference = fileReferenceDownload.fileReference().value();
        request.parameters().add(new StringValue(fileReference));
        request.parameters().add(new Int32Value(fileReferenceDownload.downloadFromOtherSourceIfNotFound() ? 0 : 1));

        connection.invokeSync(request, (double) rpcTimeout.getSeconds());
        Level logLevel = (retryCount > 0 ? LogLevel.INFO : LogLevel.DEBUG);
        if (validateResponse(request)) {
            log.log(logLevel, () -> "Request callback, OK. Req: " + request + "\nSpec: " + connection + ", retry count " + retryCount);
            if (request.returnValues().get(0).asInt32() == 0) {
                log.log(logLevel, () -> "Found file reference '" + fileReference + "' available at " + connection.getAddress());
                return true;
            } else {
                log.log(logLevel, "File reference '" + fileReference + "' not found for " + connection.getAddress());
                connectionPool.setNewCurrentConnection();
                return false;
            }
        } else {
            log.log(logLevel, "Request failed. Req: " + request + "\nSpec: " + connection.getAddress() +
                              ", error code: " + request.errorCode() + ", set error for spec, use another spec for next request" +
                              ", retry count " + retryCount);
            connectionPool.setError(connection, request.errorCode());
            return false;
        }
    }

    boolean isDownloading(FileReference fileReference) {
        synchronized (downloads) {
            return downloads.containsKey(fileReference);
        }
    }

    ListenableFuture<Optional<File>> addDownloadListener(FileReference fileReference, Runnable runnable) {
        synchronized (downloads) {
            FileReferenceDownload download = downloads.get(fileReference);
            if (download != null) {
                download.future().addListener(runnable, downloadExecutor);
                return download.future();
            }
        }
        return null;
    }

    private boolean validateResponse(Request request) {
        if (request.isError()) {
            return false;
        } else if (request.returnValues().size() == 0) {
            return false;
        } else if (!request.checkReturnTypes("is")) { // TODO: Do not hard-code return type
            log.log(LogLevel.WARNING, "Invalid return types for response: " + request.errorMessage());
            return false;
        }
        return true;
    }

    double downloadStatus(String file) {
        double status = 0.0;
        synchronized (downloads) {
            Double download = downloadStatus.get(new FileReference(file));
            if (download != null) {
                status = download;
            }
        }
        return status;
    }

    void setDownloadStatus(FileReference fileReference, double completeness) {
        synchronized (downloads) {
            downloadStatus.put(fileReference, completeness);
        }
    }

    Map<FileReference, Double> downloadStatus() {
        synchronized (downloads) {
            return ImmutableMap.copyOf(downloadStatus);
        }
    }

    public ConnectionPool connectionPool() {
        return connectionPool;
    }

    public void close() {
        try {
            downloadExecutor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.interrupted(); // Ignore and continue shutdown.
        }
    }
}
