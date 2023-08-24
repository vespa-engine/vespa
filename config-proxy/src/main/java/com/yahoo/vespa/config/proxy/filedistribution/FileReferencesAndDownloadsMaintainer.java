// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.config.util.ConfigUtils;
import com.yahoo.vespa.filedistribution.FileDownloader;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.file.Files.readAttributes;
import static java.util.logging.Level.INFO;

/**
 * Deletes file references and url downloads that have not been used for some time.
 * See {@link com.yahoo.vespa.config.proxy.filedistribution.RequestTracker} for how we track
 * when a file reference or download was last used.
 *
 * @author hmusum
 */
class FileReferencesAndDownloadsMaintainer implements Runnable {

    private static final Logger log = Logger.getLogger(FileReferencesAndDownloadsMaintainer.class.getName());
    private static final File defaultUrlDownloadDir = UrlDownloadRpcServer.defaultDownloadDirectory;
    private static final File defaultFileReferencesDownloadDir = FileDownloader.defaultDownloadDirectory;
    private static final Duration defaultDurationToKeepFiles = Duration.ofDays(30);
    private static final int defaultOutdatedFilesToKeep = 20;
    private static final Duration interval = Duration.ofMinutes(1);

    private final Optional<ScheduledExecutorService> executor;
    private final File urlDownloadDir;
    private final File fileReferencesDownloadDir;
    private final Duration durationToKeepFiles;
    private final int outDatedFilesToKeep;

    FileReferencesAndDownloadsMaintainer() {
        this(defaultFileReferencesDownloadDir, defaultUrlDownloadDir, keepFileReferencesDuration(),
             outDatedFilesToKeep(), configServers());
    }

    FileReferencesAndDownloadsMaintainer(File fileReferencesDownloadDir,
                                         File urlDownloadDir,
                                         Duration durationToKeepFiles,
                                         int outdatedFilesToKeep,
                                         List<String> configServers) {
        this.fileReferencesDownloadDir = fileReferencesDownloadDir;
        this.urlDownloadDir = urlDownloadDir;
        this.durationToKeepFiles = durationToKeepFiles;
        this.outDatedFilesToKeep = outdatedFilesToKeep;
        // Do not run on config servers
        if (configServers.contains(ConfigUtils.getCanonicalHostName())) {
            log.log(INFO, "Not running maintainer, since this is on a config server host");
            executor = Optional.empty();
        } else {
            executor = Optional.of(new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("file references and downloads cleanup")));
            executor.get().scheduleAtFixedRate(this, interval.toSeconds(), interval.toSeconds(), TimeUnit.SECONDS);
        }
    }

    @Override
    public void run() {
        if (executor.isEmpty()) return;

        try {
            deleteUnusedFiles(fileReferencesDownloadDir);
            deleteUnusedFiles(urlDownloadDir);
        } catch (Throwable t) {
            log.log(Level.WARNING, "Deleting unused files failed. ", t);
        }
    }

    public void close() {
        executor.ifPresent(ex -> {
            ex.shutdownNow();
            try {
                if (! ex.awaitTermination(10, TimeUnit.SECONDS))
                    throw new RuntimeException("Unable to shutdown " + executor + " before timeout");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void deleteUnusedFiles(File directory) {

        File[] files = directory.listFiles();
        if (files == null) return;

        List<File> filesToDelete = filesThatCanBeDeleted(files);
        filesToDelete.forEach(fileReference -> {
            if (IOUtils.recursiveDeleteDir(fileReference))
                log.log(Level.FINE, "Deleted " + fileReference.getAbsolutePath());
            else
                log.log(Level.WARNING, "Could not delete " + fileReference.getAbsolutePath());
        });
    }

    private List<File> filesThatCanBeDeleted(File[] files) {
        Instant deleteNotUsedSinceInstant = Instant.now().minus(durationToKeepFiles);

        Set<File> filesOnDisk = new HashSet<>(List.of(files));
        log.log(Level.FINE, () -> "Files on disk: " + filesOnDisk);
        int deleteCount = Math.max(0, filesOnDisk.size() - outDatedFilesToKeep);
        var canBeDeleted = filesOnDisk
                .stream()
                .peek(file -> log.log(Level.FINE, () -> file + ":" + fileLastModifiedTime(file.toPath())))
                .filter(fileReference -> isFileLastModifiedBefore(fileReference, deleteNotUsedSinceInstant))
                .sorted(Comparator.comparing(fileReference -> fileLastModifiedTime(fileReference.toPath())))
                .toList();

        // Make sure we keep some files
        canBeDeleted = canBeDeleted.subList(0, Math.min(canBeDeleted.size(), deleteCount));
        if (canBeDeleted.size() > 0)
            log.log(INFO, "Files that can be deleted (not accessed since " + deleteNotUsedSinceInstant +
                    ", will also keep " + outDatedFilesToKeep +
                    " no matter when last accessed): " + canBeDeleted);

        return canBeDeleted;
    }

    private boolean isFileLastModifiedBefore(File fileReference, Instant instant) {
        return fileLastModifiedTime(fileReference.toPath()).isBefore(instant);
    }

    private static Instant fileLastModifiedTime(Path fileReference) {
        try {
            BasicFileAttributes fileAttributes = readAttributes(fileReference, BasicFileAttributes.class);
            return fileAttributes.lastModifiedTime().toInstant();
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

    private static int outDatedFilesToKeep() {
        String env = System.getenv("VESPA_KEEP_FILE_REFERENCES_COUNT");
        if (env != null && !env.isEmpty())
            return Integer.parseInt(env);
        else
            return defaultOutdatedFilesToKeep;
    }

    private static List<String> configServers() {
        String env = System.getenv("VESPA_CONFIGSERVERS");
        if (env == null || env.isEmpty())
            return List.of(ConfigUtils.getCanonicalHostName());
        else {
            return List.of(env.split(","));
        }
    }

}
