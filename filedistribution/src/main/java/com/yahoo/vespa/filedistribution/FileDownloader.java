// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.google.common.util.concurrent.SettableFuture;
import com.yahoo.config.FileReference;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.ConnectionPool;
import com.yahoo.vespa.defaults.Defaults;

import java.io.File;
import java.time.Duration;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Handles downloads of files (file references only for now)
 *
 * @author hmusum
 */
public class FileDownloader {

    private final static Logger log = Logger.getLogger(FileDownloader.class.getName());

    private final File downloadDirectory;
    private final Duration timeout;
    private final FileReferenceDownloader fileReferenceDownloader;

    public FileDownloader(ConnectionPool connectionPool) {
        this(connectionPool,
             new File(Defaults.getDefaults().underVespaHome("var/db/vespa/filedistribution")),
             new File(Defaults.getDefaults().underVespaHome("var/db/vespa/filedistribution")),
             Duration.ofMinutes(15),
             Duration.ofSeconds(10));
    }

    FileDownloader(ConnectionPool connectionPool, File downloadDirectory, File tmpDirectory, Duration timeout, Duration sleepBetweenRetries) {
        this.downloadDirectory = downloadDirectory;
        this.timeout = timeout;
        this.fileReferenceDownloader = new FileReferenceDownloader(downloadDirectory, tmpDirectory, connectionPool, timeout, sleepBetweenRetries);
    }

    public Optional<File> getFile(FileReference fileReference) {
        return getFile(new FileReferenceDownload(fileReference));
    }

    public Optional<File> getFile(FileReferenceDownload fileReferenceDownload) {
        try {
            return getFutureFile(fileReferenceDownload).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.log(LogLevel.WARNING, "Failed downloading '" + fileReferenceDownload.fileReference().value() + "', removing from download queue: " + e.getMessage());
            fileReferenceDownloader.failedDownloading(fileReferenceDownload.fileReference());
            return Optional.empty();
        }
    }

    private Future<Optional<File>> getFutureFile(FileReferenceDownload fileReferenceDownload) {
        FileReference fileReference = fileReferenceDownload.fileReference();
        Objects.requireNonNull(fileReference, "file reference cannot be null");
        File directory = new File(downloadDirectory, fileReference.value());
        log.log(LogLevel.DEBUG, () -> "Checking if there is a file in '" + directory.getAbsolutePath() + "' ");

        Optional<File> file = getFileFromFileSystem(fileReference, directory);
        if (file.isPresent()) {
            SettableFuture<Optional<File>> future = SettableFuture.create();
            future.set(file);
            return future;
        } else {
            log.log(LogLevel.DEBUG, () -> "File reference '" + fileReference.value() + "' not found in " +
                    directory.getAbsolutePath() + ", starting download");
            return download(fileReferenceDownload);
        }
    }

    double downloadStatus(FileReference fileReference) {
        return fileReferenceDownloader.downloadStatus(fileReference.value());
    }

    public Map<FileReference, Double> downloadStatus() {
        return fileReferenceDownloader.downloadStatus();
    }

    File downloadDirectory() {
        return downloadDirectory;
    }

    private Optional<File> getFileFromFileSystem(FileReference fileReference, File directory) {
        File[] files = directory.listFiles();
        if (directory.exists() && directory.isDirectory() && files != null && files.length > 0) {
            File file = files[0];
            if (!file.exists()) {
                throw new RuntimeException("File with reference '" + fileReference.value() + "' does not exist");
            } else if (!file.canRead()) {
                throw new RuntimeException("File with reference '" + fileReference.value() + "'exists, but unable to read it");
            } else {
                fileReferenceDownloader.setDownloadStatus(fileReference, 1.0);
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    private boolean alreadyDownloaded(FileReference fileReference) {
        try {
            if (getFileFromFileSystem(fileReference, downloadDirectory).isPresent())
                return true;
        } catch (RuntimeException e) {
            /* ignored */
        }
        return false;
    }

    public boolean downloadIfNeeded(FileReferenceDownload fileReferenceDownload) {
        if (!alreadyDownloaded(fileReferenceDownload.fileReference())) {
            download(fileReferenceDownload);
            return true;
        } else {
            return false;
        }
    }

    synchronized Future<Optional<File>> download(FileReferenceDownload fileReferenceDownload) {
        FileReference fileReference = fileReferenceDownload.fileReference();
        Future<Optional<File>> inProgress = fileReferenceDownloader.addDownloadListener(fileReference, () -> getFile(fileReferenceDownload));
        if (inProgress != null) {
            log.log(LogLevel.DEBUG, () -> "Already downloading '" + fileReference.value() + "'");
            return inProgress;
        }

        Future<Optional<File>> future = queueForDownload(fileReferenceDownload);
        log.log(LogLevel.DEBUG, () -> "Queued '" + fileReference.value() + "' for download with timeout " + timeout);
        return future;
    }

    private Future<Optional<File>> queueForDownload(FileReferenceDownload fileReferenceDownload) {
        fileReferenceDownloader.addToDownloadQueue(fileReferenceDownload);
        return fileReferenceDownload.future();
    }

    public FileReferenceDownloader fileReferenceDownloader() {
        return fileReferenceDownloader;
    }
}
