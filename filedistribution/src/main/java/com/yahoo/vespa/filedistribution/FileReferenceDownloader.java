// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Downloads file reference using rpc requests to config server and keeps track of files being downloaded
 * <p>
 * Some methods are synchronized to make sure access to downloads is atomic
 *
 * @author hmusum
 */
public class FileReferenceDownloader {

    private final static Logger log = Logger.getLogger(FileReferenceDownloader.class.getName());

    private final ExecutorService downloadExecutor =
            Executors.newFixedThreadPool(Math.max(8, Runtime.getRuntime().availableProcessors()),
                                         new DaemonThreadFactory("filereference downloader"));
    private final ConnectionPool connectionPool;
    /* Ongoing downloads */
    private final Downloads downloads = new Downloads();
    /* Status for ongoing and finished downloads */
    private final DownloadStatuses downloadStatuses = new DownloadStatuses();
    private final Duration downloadTimeout;
    private final Duration sleepBetweenRetries;
    private final Duration rpcTimeout;

    FileReferenceDownloader(File downloadDirectory, File tmpDirectory, ConnectionPool connectionPool, Duration timeout, Duration sleepBetweenRetries) {
        this.connectionPool = connectionPool;
        this.downloadTimeout = timeout;
        this.sleepBetweenRetries = sleepBetweenRetries;
        // Needed to receive RPC calls receiveFile* from server after asking for files
        new FileReceiver(connectionPool.getSupervisor(), this, downloadDirectory, tmpDirectory);
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
                    Thread.sleep(sleepBetweenRetries.toMillis());
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
        downloadStatuses.add(fileReference);
        downloadExecutor.submit(() -> startDownload(fileReferenceDownload));
        return fileReferenceDownload.future();
    }

    void completedDownloading(FileReference fileReference, File file) {
        Optional<FileReferenceDownload> download = downloads.get(fileReference);
        if (download.isPresent()) {
            downloadStatuses.get(fileReference).ifPresent(DownloadStatus::finished);
            downloads.remove(fileReference);
            download.get().future().complete(Optional.of(file));
        } else {
            log.log(Level.FINE, () -> "Received '" + fileReference + "', which was not requested. Can be ignored if happening during upgrades/restarts");
        }
    }

    void failedDownloading(FileReference fileReference) {
        downloadStatuses.get(fileReference).ifPresent(d -> d.setProgress(0.0));
        downloads.remove(fileReference);
    }

    private boolean startDownloadRpc(FileReferenceDownload fileReferenceDownload, int retryCount) {
        Connection connection = connectionPool.getCurrent();
        Request request = new Request("filedistribution.serveFile");
        String fileReference = fileReferenceDownload.fileReference().value();
        request.parameters().add(new StringValue(fileReference));
        request.parameters().add(new Int32Value(fileReferenceDownload.downloadFromOtherSourceIfNotFound() ? 0 : 1));

        connection.invokeSync(request, (double) rpcTimeout.getSeconds());
        Level logLevel = (retryCount > 50 ? Level.INFO : Level.FINE);
        if (validateResponse(request)) {
            log.log(Level.FINE, () -> "Request callback, OK. Req: " + request + "\nSpec: " + connection + ", retry count " + retryCount);
            if (request.returnValues().get(0).asInt32() == 0) {
                log.log(Level.FINE, () -> "Found file reference '" + fileReference + "' available at " + connection.getAddress());
                return true;
            } else {
                log.log(logLevel, "File reference '" + fileReference + "' not found at " + connection.getAddress());
                connectionPool.setNewCurrentConnection();
                return false;
            }
        } else {
            log.log(logLevel, () -> "Request failed. Req: " + request + "\nSpec: " + connection.getAddress() +
            ", error code: " + request.errorCode() + ", will use another spec for next request" +
            ", retry count " + retryCount + ", rpc timeout " + rpcTimeout.getSeconds());
            connectionPool.setError(connection, request.errorCode());
            return false;
        }
    }

    boolean isDownloading(FileReference fileReference) {
        return downloads.get(fileReference).isPresent();
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

    double downloadStatus(String file) {
        double status = 0.0;
        Optional<DownloadStatus> downloadStatus = downloadStatuses.get(new FileReference(file));
        if (downloadStatus.isPresent()) {
            status = downloadStatus.get().progress();
        }
        return status;
    }

    void setDownloadStatus(FileReference fileReference, double completeness) {
        Optional<DownloadStatus> downloadStatus = downloadStatuses.get(fileReference);
        if (downloadStatus.isPresent())
            downloadStatus.get().setProgress(completeness);
        else
            downloadStatuses.add(fileReference, completeness);
    }

    Map<FileReference, Double> downloadStatus() {
        return downloadStatuses.all().values().stream().collect(Collectors.toMap(DownloadStatus::fileReference, DownloadStatus::progress));
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

    private static class Downloads {
        private final Map<FileReference, FileReferenceDownload> downloads = new ConcurrentHashMap<>();

        void add(FileReferenceDownload fileReferenceDownload) {
            downloads.put(fileReferenceDownload.fileReference(), fileReferenceDownload);
        }

        void remove(FileReference fileReference) {
            downloads.remove(fileReference);
        }

        Optional<FileReferenceDownload> get(FileReference fileReference) {
            return Optional.ofNullable(downloads.get(fileReference));
        }
    }

    private static class DownloadStatus {
        private final FileReference fileReference;
        private double progress; // between 0 and 1
        private final Instant created;

        DownloadStatus(FileReference fileReference) {
            this.fileReference = fileReference;
            this.progress = 0.0;
            this.created = Instant.now();
        }

        public FileReference fileReference() {
            return fileReference;
        }

        public double progress() {
            return progress;
        }

        public void setProgress(double progress) {
            this.progress = progress;
        }

        public void finished() {
            setProgress(1.0);
        }

        public Instant created() {
            return created;
        }
    }

    /* Status for ongoing and completed downloads, keeps at most status for 100 last downloads */
    private static class DownloadStatuses {

        private static final int maxEntries = 100;

        private final Map<FileReference, DownloadStatus> downloadStatus = new ConcurrentHashMap<>();

        void add(FileReference fileReference) {
            add(fileReference, 0.0);
        }

        void add(FileReference fileReference, double progress) {
            DownloadStatus ds = new DownloadStatus(fileReference);
            ds.setProgress(progress);
            downloadStatus.put(fileReference, ds);
            if (downloadStatus.size() > maxEntries) {
                Map.Entry<FileReference, DownloadStatus> oldest =
                        Collections.min(downloadStatus.entrySet(), Comparator.comparing(e -> e.getValue().created));
                downloadStatus.remove(oldest.getKey());
            }
        }

        Optional<DownloadStatus> get(FileReference fileReference) {
            return Optional.ofNullable(downloadStatus.get(fileReference));
        }

        Map<FileReference, DownloadStatus> all() {
            return Map.copyOf(downloadStatus);
        }

    }

}
