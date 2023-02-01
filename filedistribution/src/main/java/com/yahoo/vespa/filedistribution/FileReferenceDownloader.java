// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.FileReference;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.StringArray;
import com.yahoo.jrt.StringValue;
import com.yahoo.vespa.config.Connection;
import com.yahoo.vespa.config.ConnectionPool;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    private final Duration sleepBetweenRetries;
    private final Duration rpcTimeout;
    private final File downloadDirectory;

    FileReferenceDownloader(ConnectionPool connectionPool,
                            Downloads downloads,
                            Duration timeout,
                            Duration sleepBetweenRetries,
                            File downloadDirectory) {
        this.connectionPool = connectionPool;
        this.downloads = downloads;
        this.downloadTimeout = timeout;
        this.sleepBetweenRetries = sleepBetweenRetries;
        this.downloadDirectory = downloadDirectory;
        String timeoutString = System.getenv("VESPA_CONFIGPROXY_FILEDOWNLOAD_RPC_TIMEOUT");
        this.rpcTimeout = Duration.ofSeconds(timeoutString == null ? 30 : Integer.parseInt(timeoutString));
    }

    private void waitUntilDownloadStarted(FileReferenceDownload fileReferenceDownload) {
        Instant end = Instant.now().plus(downloadTimeout);
        FileReference fileReference = fileReferenceDownload.fileReference();
        int retryCount = 0;
        Connection connection = connectionPool.getCurrent();
        do {
            backoff(retryCount);

            if (FileDownloader.fileReferenceExists(fileReference, downloadDirectory))
                return;
            if (startDownloadRpc(fileReferenceDownload, retryCount, connection))
                return;

            retryCount++;
            // There might not be one connection that works for all file references (each file reference might
            // exist on just one config server, and which one could be different for each file reference), so
            // switch to a new connection for every retry
            connection = connectionPool.switchConnection(connection);
        } while (retryCount < 5 || Instant.now().isAfter(end));

        fileReferenceDownload.future().completeExceptionally(new RuntimeException("Failed getting " + fileReference));
        downloads.remove(fileReference);
    }

    private void backoff(int retryCount) {
        if (retryCount > 0) {
            try {
                long sleepTime = Math.min(120_000, (long) (Math.pow(2, retryCount)) * sleepBetweenRetries.toMillis());
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                /* ignored */
            }
        }
    }

    Future<Optional<File>> startDownload(FileReferenceDownload fileReferenceDownload) {
        FileReference fileReference = fileReferenceDownload.fileReference();
        Optional<FileReferenceDownload> inProgress = downloads.get(fileReference);
        if (inProgress.isPresent()) return inProgress.get().future();

        log.log(Level.FINE, () -> "Will download " + fileReference + " with timeout " + downloadTimeout);
        downloads.add(fileReferenceDownload);
        downloadExecutor.submit(() -> waitUntilDownloadStarted(fileReferenceDownload));
        return fileReferenceDownload.future();
    }

    void failedDownloading(FileReference fileReference) {
        downloads.remove(fileReference);
    }

    private boolean startDownloadRpc(FileReferenceDownload fileReferenceDownload, int retryCount, Connection connection) {
        Request request = createRequest(fileReferenceDownload);
        Duration rpcTimeout = rpcTimeout(retryCount);
        connection.invokeSync(request, rpcTimeout);

        Level logLevel = (retryCount > 3 ? Level.INFO : Level.FINE);
        FileReference fileReference = fileReferenceDownload.fileReference();
        if (validateResponse(request)) {
            log.log(Level.FINE, () -> "Request callback, OK. Req: " + request + "\nSpec: " + connection);
            int errorCode = request.returnValues().get(0).asInt32();
            if (errorCode == 0) {
                log.log(Level.FINE, () -> "Found " + fileReference + " available at " + connection.getAddress());
                return true;
            } else {
                log.log(logLevel, fileReference + " not found or timed out (error code " +  errorCode + ") at " + connection.getAddress());
                return false;
            }
        } else {
            log.log(logLevel, "Downloading " + fileReference + " from " + connection.getAddress() + " failed:" +
                    " error code " + request.errorCode() + " (" + request.errorMessage() + ")." +
                    " (retry " + retryCount + ", rpc timeout " + rpcTimeout + ")");
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

    private Duration rpcTimeout(int retryCount) {
        return Duration.ofSeconds(rpcTimeout.getSeconds()).plus(Duration.ofSeconds(retryCount * 5L));
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
            downloadExecutor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.interrupted(); // Ignore and continue shutdown.
        }
    }

    private static Set<CompressionType> requireNonEmpty(Set<CompressionType> s) {
        if (Objects.requireNonNull(s).isEmpty()) throw new IllegalArgumentException("set must be non-empty");
        return s;
    }

}
