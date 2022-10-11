// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.nio.file.Files.readAttributes;

/**
 * Deletes cached file references and url downloads that have not been used for some time
 *
 * @author hmusum
 */
class CachedFilesMaintainer implements Runnable {

    private final static Logger log = Logger.getLogger(CachedFilesMaintainer.class.getName());

    private static final File defaultUrlDownloadDir = UrlDownloadRpcServer.downloadDir;
    private static final File defaultFileReferencesDownloadDir = FileDownloader.defaultDownloadDirectory;
    private static final Duration defaultDurationToKeepFiles = Duration.ofDays(14);

    private final File urlDownloadDir;
    private final File fileReferencesDownloadDir;
    private final Duration durationToKeepFiles;

    CachedFilesMaintainer() {
        this(defaultFileReferencesDownloadDir, defaultUrlDownloadDir, defaultDurationToKeepFiles);
    }

    CachedFilesMaintainer(File fileReferencesDownloadDir, File urlDownloadDir, Duration durationToKeepFiles) {
        this.fileReferencesDownloadDir = fileReferencesDownloadDir;
        this.urlDownloadDir = urlDownloadDir;
        this.durationToKeepFiles = durationToKeepFiles;
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

}
