// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.FileReference;
import com.yahoo.jrt.ErrorCode;
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
import static com.yahoo.vespa.filedistribution.FileReferenceData.CompressionType.lz4;
import static com.yahoo.vespa.filedistribution.FileReferenceData.CompressionType.none;
import static com.yahoo.vespa.filedistribution.FileReferenceData.CompressionType.zstd;

/**
 * Downloads file reference from config server and keeps track of files being downloaded
 *
 * @author hmusum
 */
public class FileReferenceDownloader {

    private static final Logger log = Logger.getLogger(FileReferenceDownloader.class.getName());
    private static final Set<CompressionType> defaultAcceptedCompressionTypes = Set.of(lz4, none, zstd);

    private enum DownloadResult { SUCCESS, TIMEOUT, FAILURE, PERMISSION_DENIED }

    private record RpcResult(DownloadResult result, String message) {
        private static RpcResult of(DownloadResult result) { return new RpcResult(result, null); }
    }

    // Duplicated from MultiTenantRpcAuthorizer.JrtErrorCode (configserver module; this module can't depend on it)
    // TODO: Consider moving this to a common module to avoid duplication
    static final int jrtErrorUnauthorized = 0x20001;
    static final int jrtErrorAuthorizationFailed = 0x20002;

    private static boolean isAuthorizationError(int errorCode) {
        return errorCode == jrtErrorUnauthorized || errorCode == jrtErrorAuthorizationFailed;
    }

    // A denial can be a transient side effect of the config server not yet having registered file reference
    // ownership for a just-activated application generation, rather than a durable authorization problem.
    // Retry through this grace period before treating the denial as permanent.
    // Undocumented on purpose, might change or be removed at any time
    static final Duration defaultPermissionDeniedGracePeriod;
    static {
        var graceSeconds = System.getenv("VESPA_FILE_DOWNLOAD_PERMISSION_DENIED_GRACE_PERIOD_SECONDS");
        defaultPermissionDeniedGracePeriod = Duration.ofSeconds(graceSeconds == null ? 10 : Long.parseLong(graceSeconds));
    }

    // Undocumented on purpose, might change or be removed at any time
    static final int defaultMaxTimeoutsBeforeClose;
    static {
        var maxTimeouts = System.getenv("VESPA_FILE_DOWNLOAD_MAX_TIMEOUTS_BEFORE_CLOSE");
        defaultMaxTimeoutsBeforeClose = maxTimeouts == null ? 0 : Integer.parseInt(maxTimeouts);
    }

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
    private final int maxTimeoutsBeforeClose;
    private final Duration permissionDeniedGracePeriod;

    FileReferenceDownloader(ConnectionPool connectionPool,
                            Downloads downloads,
                            Duration timeout,
                            Duration backoffInitialTime,
                            File downloadDirectory) {
        this(connectionPool, downloads, timeout, backoffInitialTime, downloadDirectory, defaultMaxTimeoutsBeforeClose);
    }

    FileReferenceDownloader(ConnectionPool connectionPool,
                            Downloads downloads,
                            Duration timeout,
                            Duration backoffInitialTime,
                            File downloadDirectory,
                            int maxTimeoutsBeforeClose) {
        this(connectionPool, downloads, timeout, backoffInitialTime, downloadDirectory, maxTimeoutsBeforeClose,
             defaultPermissionDeniedGracePeriod);
    }

    // Package-private, for tests: allows overriding the permission-denied grace period.
    FileReferenceDownloader(ConnectionPool connectionPool,
                            Downloads downloads,
                            Duration timeout,
                            Duration backoffInitialTime,
                            File downloadDirectory,
                            int maxTimeoutsBeforeClose,
                            Duration permissionDeniedGracePeriod) {
        this.connectionPool = connectionPool;
        this.downloads = downloads;
        this.downloadTimeout = timeout;
        this.backoffInitialTime = backoffInitialTime;
        this.downloadDirectory = downloadDirectory;
        this.maxTimeoutsBeforeClose = maxTimeoutsBeforeClose;
        this.permissionDeniedGracePeriod = permissionDeniedGracePeriod;
        // Undocumented on purpose, might change or be removed at any time
        var timeoutString = Optional.ofNullable(System.getenv("VESPA_FILE_DOWNLOAD_RPC_TIMEOUT"));
        this.rpcTimeout = timeoutString.map(t -> Duration.ofSeconds(Integer.parseInt(t)));
    }

    private void waitUntilDownloadStarted(FileReferenceDownload fileReferenceDownload) {
        Instant end = Instant.now().plus(downloadTimeout);
        FileReference fileReference = fileReferenceDownload.fileReference();
        int retryCount = 0;
        int timeoutCount = 0;
        // Intentionally not reset on a non-denial result: the clock bounds how long we tolerate any sign of a
        // denial for this download attempt, regardless of what other retries/connections report in between.
        Instant permissionDeniedSince = null;
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
                    ", timeout " + timeout + " (request from " + fileReferenceDownload.client() + ")");
            if ( ! timeout.isNegative()) {
                var rpcResult = startDownloadRpc(fileReferenceDownload, retryCount, connection, timeout);
                if (rpcResult.result() == DownloadResult.SUCCESS) return;
                if (rpcResult.result() == DownloadResult.PERMISSION_DENIED) {
                    // File reference ownership may not yet be registered on the config server right after an
                    // application generation is activated - retry for a grace period before giving up, so that
                    // a transient race doesn't get treated the same as a durable authorization denial.
                    if (permissionDeniedSince == null) permissionDeniedSince = Instant.now();
                    if (!Duration.between(permissionDeniedSince, Instant.now()).minus(permissionDeniedGracePeriod).isNegative()) {
                        fileReferenceDownload.future().completeExceptionally(
                                new FileReferenceDownloadPermissionDeniedException(fileReference, rpcResult.message()));
                        downloads.remove(fileReference);
                        return;
                    }
                } else if (rpcResult.result() == DownloadResult.TIMEOUT && maxTimeoutsBeforeClose > 0) {
                    timeoutCount++;
                    if (timeoutCount >= maxTimeoutsBeforeClose) {
                        log.log(Level.INFO, "RPC request for " + fileReference + " timed out " + timeoutCount +
                                " times, closing connection to " + connection.getAddress());
                        connection.closeConnection();
                        timeoutCount = 0;
                    }
                }
            }

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

                    log.log(Level.FINE, () -> "Will download " + fileReference + " with timeout " + downloadTimeout + " from " + spec.host());
                    downloads.add(fileReferenceDownload);
                    var rpcResult = startDownloadRpc(fileReferenceDownload, 1, connection, downloadTimeout);
                    if (rpcResult.result() == DownloadResult.TIMEOUT && maxTimeoutsBeforeClose > 0) {
                        connection.closeConnection();
                    }
                    // Need to explicitly remove from downloads if downloading has not started.
                    // If downloading *has* started FileReceiver will take care of that when download has completed or failed
                    if (rpcResult.result() != DownloadResult.SUCCESS)
                        downloads.remove(fileReference);
                });
        }
    }

    void failedDownloading(FileReference fileReference) {
        downloads.remove(fileReference);
    }

    private RpcResult startDownloadRpc(FileReferenceDownload fileReferenceDownload, int retryCount, Connection connection, Duration timeout) {
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
                return RpcResult.of(DownloadResult.SUCCESS);
            } else {
                var error = FileApiErrorCodes.get(errorCode);
                var errorDescription = error == null ? "Unknown error" : error.description();
                log.log(logLevel, "Downloading " + fileReference + " from " + address + " failed (" + errorDescription + ")");
                return RpcResult.of(DownloadResult.FAILURE);
            }
        } else if (isAuthorizationError(request.errorCode())) {
            log.log(logLevel, "Downloading " + fileReference + " from " + address +
                    " (client " + fileReferenceDownload.client() + ") denied:" +
                    " error code " + request.errorCode() + " (" + request.errorMessage() + ")");
            return new RpcResult(DownloadResult.PERMISSION_DENIED, request.errorMessage());
        } else {
            log.log(logLevel, "Downloading " + fileReference + " from " + address +
                    " (client " + fileReferenceDownload.client() + ") failed:" +
                    " error code " + request.errorCode() + " (" + request.errorMessage() + ")." +
                    " (retry " + retryCount + ", rpc timeout " + timeout + ")");
            return RpcResult.of(request.errorCode() == ErrorCode.TIMEOUT ? DownloadResult.TIMEOUT : DownloadResult.FAILURE);
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
