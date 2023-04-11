// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.filedistribution.FileDownloader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.nio.file.Files.readAttributes;

/**
 * Deletes file references and url downloads that have not been used for some time.
 * See {@link com.yahoo.vespa.config.proxy.filedistribution.RequestTracker} for how we track
 * when a file reference or download was last used.
 *
 * @author hmusum
 */
class FileReferencesAndDownloadsMaintainer implements Runnable {

    private static final Logger log = Logger.getLogger(FileReferencesAndDownloadsMaintainer.class.getName());
    private static final File defaultUrlDownloadDir = UrlDownloadRpcServer.downloadDir;
    private static final File defaultFileReferencesDownloadDir = FileDownloader.defaultDownloadDirectory;
    private static final Duration defaultDurationToKeepFiles = Duration.ofDays(21);
    private static final Duration interval = Duration.ofMinutes(1);

    private final ScheduledExecutorService executor =
            new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("file references and downloads cleanup"));
    private final File urlDownloadDir;
    private final File fileReferencesDownloadDir;
    private final Duration durationToKeepFiles;

    FileReferencesAndDownloadsMaintainer() {
        this(defaultFileReferencesDownloadDir, defaultUrlDownloadDir, keepFileReferencesDuration());
    }

    FileReferencesAndDownloadsMaintainer(File fileReferencesDownloadDir, File urlDownloadDir, Duration durationToKeepFiles) {
        this.fileReferencesDownloadDir = fileReferencesDownloadDir;
        this.urlDownloadDir = urlDownloadDir;
        this.durationToKeepFiles = durationToKeepFiles;
        executor.scheduleAtFixedRate(this, interval.toSeconds(), interval.toSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        try {
            deleteUnusedFiles(fileReferencesDownloadDir);
            deleteUnusedFiles(urlDownloadDir);
        } catch (Throwable t) {
            log.log(Level.WARNING, "Deleting unused files failed. ", t);
        }
    }

    public void close() {
        executor.shutdownNow();
        try {
            if ( ! executor.awaitTermination(10, TimeUnit.SECONDS))
                throw new RuntimeException("Unable to shutdown " + executor + " before timeout");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteUnusedFiles(File directory) {
        Instant deleteNotUsedSinceInstant = Instant.now().minus(durationToKeepFiles);
        Set<String> filesOnDisk = new HashSet<>();
        File[] files = directory.listFiles();
        if (files != null)
            filesOnDisk.addAll(Arrays.stream(files).map(File::getName).collect(Collectors.toSet()));
        log.log(Level.FINE, () -> "Files on disk (in " + directory + "): " + filesOnDisk);

        Set<String> filesToDelete = filesOnDisk
                .stream()
                .filter(fileReference -> isFileLastModifiedBefore(new File(directory, fileReference), deleteNotUsedSinceInstant))
                .collect(Collectors.toSet());
        if (filesToDelete.size() > 0) {
            log.log(Level.INFO, "Files that can be deleted in " + directory + " (not used since " + deleteNotUsedSinceInstant + "): " + filesToDelete);
            filesToDelete.forEach(fileReference -> {
                File file = new File(directory, fileReference);
                if (!IOUtils.recursiveDeleteDir(file))
                    log.log(Level.WARNING, "Could not delete " + file.getAbsolutePath());
            });
        }
    }

    private boolean isFileLastModifiedBefore(File fileReference, Instant instant) {
        BasicFileAttributes fileAttributes;
        try {
            fileAttributes = readAttributes(fileReference.toPath(), BasicFileAttributes.class);
            return fileAttributes.lastModifiedTime().toInstant().isBefore(instant);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Duration keepFileReferencesDuration() {
        String env = System.getenv("VESPA_KEEP_FILE_REFERENCES_DAYS");
        if (env != null && !env.isEmpty())
            return Duration.ofDays(Integer.parseInt(env));
        else
            return defaultDurationToKeepFiles;
    }

}
