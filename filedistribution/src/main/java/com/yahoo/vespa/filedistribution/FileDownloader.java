// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.jrt.Supervisor;
import com.yahoo.vespa.config.Connection;
import com.yahoo.vespa.config.ConnectionPool;
import com.yahoo.vespa.config.JRTConnectionPool;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.yolean.Exceptions;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private final static Logger log = Logger.getLogger(FileDownloader.class.getName());
    public static File defaultDownloadDirectory = new File(Defaults.getDefaults().underVespaHome("var/db/vespa/filedistribution"));

    private final ConnectionPool connectionPool;
    private final Supervisor supervisor;
    private final File downloadDirectory;
    private final Duration timeout;
    private final FileReferenceDownloader fileReferenceDownloader;
    private final Downloads downloads;

    public FileDownloader(List<String> configservers, Supervisor supervisor) {
        this(getConnectionPool(configservers, supervisor), supervisor);
    }

    public FileDownloader(ConnectionPool connectionPool, Supervisor supervisor) {
        this(connectionPool, supervisor, defaultDownloadDirectory, new Downloads());
    }

    public FileDownloader(ConnectionPool connectionPool, Supervisor supervisor, File downloadDirectory, Downloads downloads) {
        // TODO: Reduce timeout even more, timeout is so long that we might get starvation
        this(connectionPool, supervisor, downloadDirectory, downloads, Duration.ofMinutes(5), Duration.ofSeconds(10));
    }

    public FileDownloader(ConnectionPool connectionPool, Supervisor supervisor, File downloadDirectory, Downloads downloads,
                          Duration timeout, Duration sleepBetweenRetries) {
        this.connectionPool = connectionPool;
        this.supervisor = supervisor;
        this.downloadDirectory = downloadDirectory;
        this.timeout = timeout;
        // Needed to receive RPC receiveFile* calls from server after asking for files
        new FileReceiver(supervisor, downloads, downloadDirectory);
        this.fileReferenceDownloader = new FileReferenceDownloader(connectionPool, downloads, timeout, sleepBetweenRetries);
        this.downloads = downloads;
    }

    public Optional<File> getFile(FileReference fileReference) {
        return getFile(new FileReferenceDownload(fileReference));
    }

    public Optional<File> getFile(FileReferenceDownload fileReferenceDownload) {
        try {
            return getFutureFile(fileReferenceDownload).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.log(Level.WARNING, "Failed downloading '" + fileReferenceDownload +
                                   "', removing from download queue: " + Exceptions.toMessageString(e));
            fileReferenceDownloader.failedDownloading(fileReferenceDownload.fileReference());
            return Optional.empty();
        }
    }

    Future<Optional<File>> getFutureFile(FileReferenceDownload fileReferenceDownload) {
        FileReference fileReference = fileReferenceDownload.fileReference();
        Objects.requireNonNull(fileReference, "file reference cannot be null");

        Optional<File> file = getFileFromFileSystem(fileReference);
        return (file.isPresent())
                ? CompletableFuture.completedFuture(file)
                : download(fileReferenceDownload);
    }

    public Map<FileReference, Double> downloadStatus() { return downloads.downloadStatus(); }

    public ConnectionPool connectionPool() { return connectionPool; }

    File downloadDirectory() {
        return downloadDirectory;
    }

    private Optional<File> getFileFromFileSystem(FileReference fileReference) {
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
            downloads.setDownloadStatus(fileReference, 1.0);
            return Optional.of(file);
        }
    }

    boolean isDownloading(FileReference fileReference) {
        return downloads.get(fileReference).isPresent();
    }

    private boolean alreadyDownloaded(FileReferenceDownload fileReferenceDownload) {
        try {
            return getFileFromFileSystem(fileReferenceDownload.fileReference()).isPresent();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Start a download, don't wait for result */
    public void downloadIfNeeded(FileReferenceDownload fileReferenceDownload) {
        if (alreadyDownloaded(fileReferenceDownload)) return;

        download(fileReferenceDownload);
    }

    /** Download, the future returned will be complete()d by receiving method in {@link FileReceiver} */
    private synchronized Future<Optional<File>> download(FileReferenceDownload fileReferenceDownload) {
        return fileReferenceDownloader.download(fileReferenceDownload);
    }

    public void close() {
        fileReferenceDownloader.close();
        supervisor.transport().shutdown().join();
    }

    private static ConnectionPool getConnectionPool(List<String> configServers, Supervisor supervisor) {
        return configServers.size() > 0
                ? new JRTConnectionPool(new ConfigSourceSet(configServers), supervisor)
                : emptyConnectionPool();
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
