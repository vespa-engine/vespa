// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.FileReference;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.StringValue;
import com.yahoo.vespa.config.Connection;
import com.yahoo.vespa.config.ConnectionPool;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Downloads file reference using rpc requests to config server and keeps track of files being downloaded
 *
 * @author hmusum
 */
public class FileReferenceDownloader {

    private final static Logger log = Logger.getLogger(FileReferenceDownloader.class.getName());

    private final ExecutorService downloadExecutor =
            Executors.newFixedThreadPool(Math.max(8, Runtime.getRuntime().availableProcessors()),
                                         new DaemonThreadFactory("filereference downloader"));
    private final ConnectionPool connectionPool;
    private final Downloads downloads;
    private final Duration downloadTimeout;
    private final Duration sleepBetweenRetries;
    private final Duration rpcTimeout;

    FileReferenceDownloader(ConnectionPool connectionPool,
                            Downloads downloads,
                            Duration timeout,
                            Duration sleepBetweenRetries) {
        this.connectionPool = connectionPool;
        this.downloads = downloads;
        this.downloadTimeout = timeout;
        this.sleepBetweenRetries = sleepBetweenRetries;
        String timeoutString = System.getenv("VESPA_CONFIGPROXY_FILEDOWNLOAD_RPC_TIMEOUT");
        this.rpcTimeout = Duration.ofSeconds(timeoutString == null ? 30 : Integer.parseInt(timeoutString));
    }

    private void startDownload(FileReferenceDownload fileReferenceDownload) {
        FileReference fileReference = fileReferenceDownload.fileReference();
        Instant end = Instant.now().plus(downloadTimeout);
        boolean downloadStarted = false;
        int retryCount = 0;
        do {
            try {
                if (startDownloadRpc(fileReferenceDownload, retryCount)) {
                    downloadStarted = true;
                } else {
                    retryCount++;
                    long sleepTime = Math.min(sleepBetweenRetries.toMillis() * retryCount,
                                              Math.max(0, Duration.between(Instant.now(), end).toMillis()));
                    Thread.sleep(sleepTime);
                }
            }
            catch (InterruptedException e) { /* ignored */}
        } while (Instant.now().isBefore(end) && !downloadStarted);

        if ( !downloadStarted) {
            fileReferenceDownload.future().completeExceptionally(new RuntimeException("Failed getting file reference '" + fileReference.value() + "'"));
            downloads.remove(fileReference);
        }
    }

    Future<Optional<File>> download(FileReferenceDownload fileReferenceDownload) {
        FileReference fileReference = fileReferenceDownload.fileReference();
        Optional<FileReferenceDownload> inProgress = downloads.get(fileReference);
        if (inProgress.isPresent()) return inProgress.get().future();

        log.log(Level.FINE, () -> "Will download file reference '" + fileReference.value() + "' with timeout " + downloadTimeout);
        downloads.add(fileReferenceDownload);
        downloadExecutor.submit(() -> startDownload(fileReferenceDownload));
        return fileReferenceDownload.future();
    }

    void failedDownloading(FileReference fileReference) {
        downloads.remove(fileReference);
    }

    private boolean startDownloadRpc(FileReferenceDownload fileReferenceDownload, int retryCount) {
        Connection connection = connectionPool.getCurrent();
        Request request = new Request("filedistribution.serveFile");
        String fileReference = fileReferenceDownload.fileReference().value();
        request.parameters().add(new StringValue(fileReference));
        request.parameters().add(new Int32Value(fileReferenceDownload.downloadFromOtherSourceIfNotFound() ? 0 : 1));
        double timeoutSecs = (double) rpcTimeout.getSeconds();
        timeoutSecs += retryCount * 10.0;
        connection.invokeSync(request, timeoutSecs);
        Level logLevel = (retryCount > 5 ? Level.INFO : Level.FINE);
        if (validateResponse(request)) {
            log.log(Level.FINE, () -> "Request callback, OK. Req: " + request + "\nSpec: " + connection + ", retry count " + retryCount);
            if (request.returnValues().get(0).asInt32() == 0) {
                log.log(Level.FINE, () -> "Found file reference '" + fileReference + "' available at " + connection.getAddress());
                return true;
            } else {
                log.log(logLevel, "File reference '" + fileReference + "' not found at " + connection.getAddress());
                connectionPool.switchConnection(connection);
                return false;
            }
        } else {
            log.log(logLevel, () -> "Downloading file " + fileReference + " from " + connection.getAddress() + " failed: " +
                                    request + ", error: " + request.errorMessage() + ", will use another config server for next request" +
                                    " (retry count " + retryCount + ", rpc timeout " + rpcTimeout.getSeconds() + ")");
            connectionPool.switchConnection(connection);
            return false;
        }
    }

    private boolean validateResponse(Request request) {
        if (request.isError()) {
            return false;
        } else if (request.returnValues().size() == 0) {
            return false;
        } else if (!request.checkReturnTypes("is")) { // TODO: Do not hard-code return type
            log.log(Level.WARNING, "Invalid return types for response: " + request.errorMessage());
            return false;
        }
        return true;
    }

    public void close() {
        downloadExecutor.shutdown();
        try {
            downloadExecutor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.interrupted(); // Ignore and continue shutdown.
        }
    }

}
