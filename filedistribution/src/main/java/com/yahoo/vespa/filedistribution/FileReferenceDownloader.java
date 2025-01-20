// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.FileReference;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringArray;
import com.yahoo.jrt.StringValue;
import com.yahoo.vespa.config.Connection;
import com.yahoo.vespa.config.ConnectionPool;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.filedistribution.FileReferenceData.CompressionType;
import static com.yahoo.vespa.filedistribution.FileReferenceData.CompressionType.gzip;
import static com.yahoo.vespa.filedistribution.FileReferenceData.CompressionType.lz4;
import static com.yahoo.vespa.filedistribution.FileReferenceData.CompressionType.zstd;

/**
 * Downloads file reference from config server and keeps track of files being downloaded
 *
 * @author hmusum
 */
public class FileReferenceDownloader {

    private static final Logger log = Logger.getLogger(FileReferenceDownloader.class.getName());
    private static final Set<CompressionType> defaultAcceptedCompressionTypes = Set.of(gzip, lz4, zstd);

    private final ExecutorService downloadExecutor =
            Executors.newFixedThreadPool(Math.max(8, Runtime.getRuntime().availableProcessors()),
                                         new DaemonThreadFactory("filereference downloader"));
    private final ConnectionPool connectionPool;
    private final Downloads downloads;
    private final Duration downloadTimeout;
    private final Duration backoffInitialTime;
    private final Optional<Duration> rpcTimeout; // Only used when overridden with env variable
    private final File downloadDirectory;
    private final AtomicBoolean shutDown = new AtomicBoolean(false);

    FileReferenceDownloader(ConnectionPool connectionPool,
                            Downloads downloads,
                            Duration timeout,
                            Duration backoffInitialTime,
                            File downloadDirectory) {
        this.connectionPool = connectionPool;
        this.downloads = downloads;
        this.downloadTimeout = timeout;
        this.backoffInitialTime = backoffInitialTime;
        this.downloadDirectory = downloadDirectory;
        // Undocumented on purpose, might change or be removed at any time
        var timeoutString = Optional.ofNullable(System.getenv("VESPA_FILE_DOWNLOAD_RPC_TIMEOUT"));
        this.rpcTimeout = timeoutString.map(t -> Duration.ofSeconds(Integer.parseInt(t)));
    }

    private void waitUntilDownloadStarted(FileReferenceDownload fileReferenceDownload) {
        Instant end = Instant.now().plus(downloadTimeout);
        FileReference fileReference = fileReferenceDownload.fileReference();
        int retryCount = 0;
        Connection connection = connectionPool.getCurrent();
        do {
            if (retryCount > 0)
                backoff(retryCount, end);

            if (shutDown.get())
                return;
            if (FileDownloader.fileReferenceExists(fileReference, downloadDirectory))
                return;
            var timeout = rpcTimeout.orElse(Duration.between(Instant.now(), end));
            log.log(Level.FINE, "Wait until download of " + fileReference + " has started, retryCount " + retryCount +
                    " timeout" + timeout + " (request from client " + fileReferenceDownload.client() + ")");
            if ( ! timeout.isNegative() && startDownloadRpc(fileReferenceDownload, retryCount, connection, timeout))
                return;

            retryCount++;
            // There might not be one connection that works for all file references (each file reference might
            // exist on just one config server, and which one could be different for each file reference), so
            // switch to a new connection for every retry
            connection = connectionPool.switchConnection(connection);
        } while (Instant.now().isBefore(end));

        fileReferenceDownload.future().completeExceptionally(new RuntimeException("Failed getting " + fileReference));
        downloads.remove(fileReference);
    }

    private void backoff(int retryCount, Instant end) {
        try {
            long sleepTime = Math.min(120_000,
                                      Math.min((long) (Math.pow(2, retryCount)) * backoffInitialTime.toMillis(),
                                               Duration.between(Instant.now(), end).toMillis()));
            if (sleepTime <= 0) return;

            var endSleep = Instant.now().plusMillis(sleepTime);
            do {
                Thread.sleep(Math.min(100, sleepTime));
            } while (Instant.now().isBefore(endSleep) && ! shutDown.get());
        } catch (InterruptedException e) {
            /* ignored */
        }
    }

    CompletableFuture<Optional<File>> startDownload(FileReferenceDownload fileReferenceDownload) {
        FileReference fileReference = fileReferenceDownload.fileReference();
        Optional<FileReferenceDownload> inProgress = downloads.get(fileReference);
        if (inProgress.isPresent()) return inProgress.get().future();

        downloads.add(fileReferenceDownload);
        downloadExecutor.submit(() -> waitUntilDownloadStarted(fileReferenceDownload));
        return fileReferenceDownload.future();
    }

    void startDownloadFromSource(FileReferenceDownload fileReferenceDownload, Spec spec) {
        FileReference fileReference = fileReferenceDownload.fileReference();

        for (var connection : connectionPool.connections()) {
            if (connection.getAddress().equals(spec.toString()))
                downloadExecutor.submit(() -> {
                    if (downloads.get(fileReference).isPresent()) return;

                    log.log(Level.FINE, () -> "Will download " + fileReference + " with timeout " + downloadTimeout + " from " + spec);
                    downloads.add(fileReferenceDownload);
                    var downloading = startDownloadRpc(fileReferenceDownload, 1, connection, downloadTimeout);
                    // Need to explicitly remove from downloads if downloading has not started.
                    // If downloading *has* started FileReceiver will take care of that when download has completed or failed
                    if ( ! downloading)
                        downloads.remove(fileReference);
                });
        }
    }

    void failedDownloading(FileReference fileReference) {
        downloads.remove(fileReference);
    }

    private boolean startDownloadRpc(FileReferenceDownload fileReferenceDownload, int retryCount, Connection connection, Duration timeout) {
        Request request = createRequest(fileReferenceDownload);
        connection.invokeSync(request, timeout);

        Level logLevel = (retryCount > 3 ? Level.INFO : Level.FINE);
        FileReference fileReference = fileReferenceDownload.fileReference();
        String address = connection.getAddress();
        if (validateResponse(request)) {
            log.log(Level.FINE, () -> "Request callback, OK. Req: " + request + "\nSpec: " + connection);
            int errorCode = request.returnValues().get(0).asInt32();

            if (errorCode == 0) {
                log.log(Level.FINE, () -> "Found " + fileReference + " available at " + address);
                return true;
            } else {
                var error = FileApiErrorCodes.get(errorCode);
                log.log(logLevel, "Downloading " + fileReference + " from " + address + " failed (" + error + ")");
                return false;
            }
        } else {
            log.log(logLevel, "Downloading " + fileReference + " from " + address + " failed:" +
                    " error code " + request.errorCode() + " (" + request.errorMessage() + ")." +
                    " (retry " + retryCount + ", rpc timeout " + timeout + ")");
            return false;
        }
    }

    private Request createRequest(FileReferenceDownload fileReferenceDownload) {
        Request request = new Request("filedistribution.serveFile");
        request.parameters().add(new StringValue(fileReferenceDownload.fileReference().value()));
        request.parameters().add(new Int32Value(fileReferenceDownload.downloadFromOtherSourceIfNotFound() ? 0 : 1));
        String[] temp = new String[defaultAcceptedCompressionTypes.size()];
        defaultAcceptedCompressionTypes.stream().map(Enum::name).toList().toArray(temp);
        request.parameters().add(new StringArray(temp));
        return request;
    }

    private boolean validateResponse(Request request) {
        if (request.isError()) {
            return false;
        } else if (request.returnValues().size() == 0) {
            return false;
        } else if (!request.checkReturnTypes("is")) {
            log.log(Level.WARNING, "Invalid return types for response: " + request.errorMessage());
            return false;
        }
        return true;
    }

    public void close() {
        shutDown.set(true);
        downloadExecutor.shutdown();
        try {
            if (!downloadExecutor.awaitTermination(30, TimeUnit.SECONDS))
                log.log(Level.WARNING, "FileReferenceDownloader failed to shutdown within 30 seconds");
        } catch (InterruptedException e) {
            Thread.interrupted(); // Ignore and continue shutdown.
        }
    }

}
