// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import com.yahoo.vespa.filedistribution.FileDownloader;
import com.yahoo.vespa.filedistribution.maintenance.FileDistributionCleanup;
import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Deletes file references and url downloads on disk that have not been used for some time
 *
 * @author hmusum
 */
class CachedFilesMaintainer implements Runnable {

    private final static Logger log = Logger.getLogger(CachedFilesMaintainer.class.getName());

    private static final File defaultUrlDownloadDir = UrlDownloadRpcServer.downloadDir;
    private static final File defaultFileReferencesDownloadDir = FileDownloader.defaultDownloadDirectory;
    private static final Duration defaultDurationToKeepFiles = Duration.ofDays(20);
    private static final int defaultKeepCount = 20;

    private final File urlDownloadDir;
    private final File fileReferencesDownloadDir;
    private final Duration durationToKeepFiles;
    private final FileDistributionCleanup cleanup;
    private final int keepCount;  // keep this many files no matter how old they are or when they were last accessed

    CachedFilesMaintainer() {
        this(defaultFileReferencesDownloadDir, defaultUrlDownloadDir, defaultDurationToKeepFiles, Clock.systemUTC(), defaultKeepCount);
    }

    CachedFilesMaintainer(File fileReferencesDownloadDir,
                          File urlDownloadDir,
                          Duration durationToKeepFiles,
                          Clock clock,
                          int keepCount) {
        this.fileReferencesDownloadDir = fileReferencesDownloadDir;
        this.urlDownloadDir = urlDownloadDir;
        this.durationToKeepFiles = durationToKeepFiles;
        this.cleanup = new FileDistributionCleanup(clock);
        this.keepCount = keepCount;
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
        cleanup.deleteUnusedFileReferences(directory, durationToKeepFiles, keepCount, Set.of());
    }

}
