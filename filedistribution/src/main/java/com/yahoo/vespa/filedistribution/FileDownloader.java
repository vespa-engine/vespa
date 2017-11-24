//  Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.yahoo.config.FileReference;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.ConnectionPool;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.yolean.Exceptions;

import java.io.File;
import java.time.Duration;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
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
                Duration.ofMinutes(15));
    }

    FileDownloader(ConnectionPool connectionPool, File downloadDirectory, Duration timeout) {
        this.downloadDirectory = downloadDirectory;
        this.timeout = timeout;
        this.fileReferenceDownloader = new FileReferenceDownloader(downloadDirectory, connectionPool, timeout);
    }

    public Optional<File> getFile(FileReference fileReference) {
        Objects.requireNonNull(fileReference, "file reference cannot be null");
        File directory = new File(downloadDirectory, fileReference.value());
        log.log(LogLevel.DEBUG, "Checking if there is a file in '" + directory.getAbsolutePath() + "' ");

        Optional<File> file = getFileFromFileSystem(fileReference, directory);
        if (file.isPresent()) {
            return file;
        } else {
            log.log(LogLevel.INFO, "File reference '" + fileReference.value() + "' not found in " +
                    directory.getAbsolutePath() + ", starting download");
            return queueForDownload(fileReference, timeout);
        }
    }

    public void queueForDownload(List<FileReference> fileReferences) {
        fileReferences.forEach(this::queueForDownload);
    }

    public void receiveFile(FileReference fileReference, String filename, byte[] content, long xxHash) {
        fileReferenceDownloader.receiveFile(fileReference, filename, content, xxHash);
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
            if (files.length != 1) {
                throw new RuntimeException("More than one file in  '" + fileReference.value() +
                        "', expected only one, unable to proceed");
            }
            File file = files[0];
            if (!file.exists()) {
                throw new RuntimeException("File with reference '" + fileReference.value() +
                        "' does not exist");
            } else if (!file.canRead()) {
                throw new RuntimeException("File with reference '" + fileReference.value() +
                        "'exists, but unable to read it");
            } else {
                fileReferenceDownloader.setDownloadStatus(fileReference.value(), 100.0);
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    private synchronized Optional<File> queueForDownload(FileReference fileReference, Duration timeout) {
        if (fileReferenceDownloader.isDownloading(fileReference)) {
            log.log(LogLevel.INFO, "Already downloading '" + fileReference.value() + "'");
            ListenableFuture<Optional<File>> future =
                    fileReferenceDownloader.addDownloadListener(fileReference, () -> getFile(fileReference));
            try {
                return future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Failed downloading file reference '" + fileReference.value() + "': " +
                        Exceptions.toMessageString(e));
            }
        }

        SettableFuture<Optional<File>> future = SettableFuture.create();
        queueForDownload(new FileReferenceDownload(fileReference, future));
        log.log(LogLevel.INFO, "Queued '" + fileReference.value() + "' for download with timeout " + timeout);

        try {
            Optional<File> fileDownloaded;
            try {
                log.log(LogLevel.INFO, "Waiting for '" + fileReference.value() + "' to download");
                fileDownloaded = future.get(timeout.getSeconds() - 1, TimeUnit.SECONDS);
                log.log(LogLevel.INFO, "'" + fileReference.value() + "' downloaded");
            } catch (TimeoutException e) {
                log.log(LogLevel.WARNING, "Downloading '" + fileReference.value() + "' timed out");
                return Optional.empty();
            }
            return fileDownloaded;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Could not download '" + fileReference.value() + "'");
        }
    }

    // We don't care about the future in this call
    private synchronized void queueForDownload(FileReference fileReference) {
        queueForDownload(new FileReferenceDownload(fileReference, SettableFuture.create()));
    }

    private synchronized void queueForDownload(FileReferenceDownload fileReferenceDownload) {
        fileReferenceDownloader.addToDownloadQueue(fileReferenceDownload);
    }

    public FileReferenceDownloader fileReferenceDownloader() {
        return fileReferenceDownloader;
    }
}
