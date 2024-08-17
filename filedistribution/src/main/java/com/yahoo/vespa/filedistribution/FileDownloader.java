// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.vespa.config.Connection;
import com.yahoo.vespa.config.ConnectionPool;
import com.yahoo.vespa.defaults.Defaults;
import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles downloads of files (file references only for now)
 *
 * @author hmusum
 */
public class FileDownloader implements AutoCloseable {

    private static final Logger log = Logger.getLogger(FileDownloader.class.getName());
    private static final Duration backoffInitialTime;
    public static final File defaultDownloadDirectory =
            new File(Defaults.getDefaults().underVespaHome("var/db/vespa/filedistribution"));
    // Undocumented on purpose, might change or be removed at any time
    private static final boolean forceDownload = Boolean.parseBoolean(System.getenv("VESPA_FORCE_DOWNLOAD_OF_FILE_REFERENCES"));

    private final ConnectionPool connectionPool;
    private final Supervisor supervisor;
    private final File downloadDirectory;
    private final Duration timeout;
    private final FileReferenceDownloader fileReferenceDownloader;
    private final Downloads downloads = new Downloads();

    static {
        // Undocumented on purpose, might change or be removed at any time
        var backOff = System.getenv("VESPA_FILE_DOWNLOAD_BACKOFF_INITIAL_TIME_MS");
        backoffInitialTime = Duration.ofMillis(backOff == null ? 5000 : Long.parseLong(backOff));
    }

    public FileDownloader(ConnectionPool connectionPool, Supervisor supervisor, Duration timeout) {
        this(connectionPool, supervisor, defaultDownloadDirectory, timeout, backoffInitialTime);
    }

    public FileDownloader(ConnectionPool connectionPool, Supervisor supervisor, File downloadDirectory, Duration timeout) {
        this(connectionPool, supervisor, downloadDirectory, timeout, backoffInitialTime);
    }

    public FileDownloader(ConnectionPool connectionPool,
                          Supervisor supervisor,
                          File downloadDirectory,
                          Duration timeout,
                          Duration backoffInitialTime) {
        this.connectionPool = connectionPool;
        this.supervisor = supervisor;
        this.downloadDirectory = downloadDirectory;
        this.timeout = timeout;
        // Needed to receive RPC receiveFile* calls from server after starting download of file reference
        new FileReceiver(supervisor, downloads, downloadDirectory);
        this.fileReferenceDownloader = new FileReferenceDownloader(connectionPool,
                                                                   downloads,
                                                                   timeout,
                                                                   backoffInitialTime,
                                                                   downloadDirectory);
        if (forceDownload)
            log.log(Level.INFO, "Force download of file references (download even if file reference exists on disk)");
    }

    public Optional<File> getFile(FileReferenceDownload fileReferenceDownload) {
        try {
            return getFutureFile(fileReferenceDownload).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fileReferenceDownloader.failedDownloading(fileReferenceDownload.fileReference());
            return Optional.empty();
        }
    }

    /** Returns a future that times out if download takes too long, and return empty on unsuccessful download. */
    public Future<Optional<File>> getFutureFileOrTimeout(FileReferenceDownload fileReferenceDownload) {
        return getFutureFile(fileReferenceDownload)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(thrown -> {
                    fileReferenceDownloader.failedDownloading(fileReferenceDownload.fileReference());
                    return Optional.empty();
                });
    }

    CompletableFuture<Optional<File>> getFutureFile(FileReferenceDownload fileReferenceDownload) {
        FileReference fileReference = fileReferenceDownload.fileReference();

        Optional<File> file = getFileFromFileSystem(fileReference);
        if (file.isPresent()) {
            downloads.setDownloadStatus(fileReference, 1.0);
            return CompletableFuture.completedFuture(file);
        } else {
            return startDownload(fileReferenceDownload);
        }
    }

    public Map<FileReference, Double> downloadStatus() { return downloads.downloadStatus(); }

    public ConnectionPool connectionPool() { return connectionPool; }

    public Downloads downloads() { return downloads; }

    File downloadDirectory() {
        return downloadDirectory;
    }

    private Optional<File> getFileFromFileSystem(FileReference fileReference) {
        return getFileFromFileSystem(fileReference, downloadDirectory);
    }

    private static Optional<File> getFileFromFileSystem(FileReference fileReference, File downloadDirectory) {
        if (forceDownload) return Optional.empty();

        File[] files = new File(downloadDirectory, fileReference.value()).listFiles();
        if (files == null) return Optional.empty();
        if (files.length == 0) return Optional.empty();
        if (files.length > 1) throw new RuntimeException("More than one file reference found for " + fileReference);

        File file = files[0];
        if (!file.exists()) {
            throw new RuntimeException("File reference '" + fileReference.value() + "' does not exist");
        } else if (!file.canRead()) {
            throw new RuntimeException("File reference '" + fileReference.value() + "' exists, but unable to read it");
        } else {
            log.log(Level.FINE, () -> "File reference '" + fileReference.value() + "' found: " + file.getAbsolutePath());
            return Optional.of(file);
        }
    }

    static boolean fileReferenceExists(FileReference fileReference, File downloadDirectory) {
        return getFileFromFileSystem(fileReference, downloadDirectory).isPresent();
    }

    boolean isDownloading(FileReference fileReference) {
        return downloads.get(fileReference).isPresent();
    }

    /** Start a download from the specified source, don't wait for result
     *  @return true if download was started, false if file reference already exists
     */
    public boolean downloadFromSource(FileReferenceDownload fileReferenceDownload, Spec source) {
        if (fileReferenceExists(fileReferenceDownload.fileReference(), downloadDirectory)) return false;

        fileReferenceDownloader.startDownloadFromSource(fileReferenceDownload, source);
        return true;
    }

    /** Start downloading, the future returned will be complete()d by receiving method in {@link FileReceiver} */
    private synchronized CompletableFuture<Optional<File>> startDownload(FileReferenceDownload fileReferenceDownload) {
        return fileReferenceDownloader.startDownload(fileReferenceDownload);
    }

    public void close() {
        fileReferenceDownloader.close();
        supervisor.transport().shutdown().join();
    }

    public static ConnectionPool emptyConnectionPool() {
        return new EmptyConnectionPool();
    }

    private static class EmptyConnectionPool implements ConnectionPool {

        @Override
        public void close() { }

        @Override
        public Connection getCurrent() { return null; }

        @Override
        public Connection switchConnection(Connection connection) { return null; }

        @Override
        public int getSize() { return 0; }

    }

}
