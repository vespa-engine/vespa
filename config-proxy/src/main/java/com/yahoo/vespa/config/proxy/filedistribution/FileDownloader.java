// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.FileReference;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.defaults.Defaults;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * Keeps track of files to download and download status
 *
 * @author hmusum
 */
public class FileDownloader {
    private final static Logger log = Logger.getLogger(FileDownloader.class.getName());


    private final String filesDirectory;
    private final ConfigSourceSet configSourceSet;
    private final Duration timeout;
    private final Map<FileReference, Double> downloadStatus = new HashMap<>();
    private final Set<FileReference> queuedForDownload = new LinkedHashSet<>();

    public FileDownloader(ConfigSourceSet configSourceSet) {
        this(configSourceSet,
                Defaults.getDefaults().underVespaHome("var/db/vespa/filedistribution"),
                Duration.ofMinutes(15));
    }

    FileDownloader(ConfigSourceSet configSourceSet, String filesDirectory, Duration timeout) {
        this.configSourceSet = configSourceSet;
        this.filesDirectory = filesDirectory;
        this.timeout = timeout;
    }

    public Optional<File> getFile(FileReference fileReference) {
        Objects.requireNonNull(fileReference, "file reference cannot be null");
        File directory = new File(filesDirectory, fileReference.value()); // directory with one file

        log.log(LogLevel.DEBUG, "Checking if there is a file in '" + directory.getAbsolutePath() + "' ");
        Instant end = Instant.now().plus(timeout);
        do {
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
                    downloadStatus.put(fileReference, 100.0);
                    return Optional.of(file);
                }
            } else {
                queueForDownload(fileReference);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } while (Instant.now().isBefore(end));

        return Optional.empty();
    }

    public Map<FileReference, Double> downloadStatus() {
        return downloadStatus;
    }

    public void queueForDownload(List<FileReference> fileReferences) {
        fileReferences.forEach(this::queueForDownload);
    }

    private void queueForDownload(FileReference fileReference) {
        log.log(LogLevel.INFO, "Queued '" + fileReference.value() + "' for download ");
        queuedForDownload.add(fileReference);
        downloadStatus.put(fileReference, 0.0);
    }

    ImmutableSet<FileReference> queuedForDownload() {
        return ImmutableSet.copyOf(queuedForDownload);
    }

}