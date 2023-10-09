// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;

import java.io.File;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Keeps track of downloads and download status
 *
 * @author hmusum
 */
public class Downloads {

    private static final Logger log = Logger.getLogger(Downloads.class.getName());

    private final Map<FileReference, FileReferenceDownload> downloads = new ConcurrentHashMap<>();
    private final DownloadStatuses downloadStatuses = new DownloadStatuses();

    public DownloadStatuses downloadStatuses() { return downloadStatuses; }

    void setDownloadStatus(FileReference fileReference, double completeness) {
        downloadStatuses.put(fileReference, completeness);
    }

    void completedDownloading(FileReference fileReference, File file) {
        Optional<FileReferenceDownload> download = get(fileReference);
        setDownloadStatus(fileReference, 1.0);
        if (download.isPresent()) {
            downloads.remove(fileReference);
            download.get().future().complete(Optional.of(file));
        } else {
            log.log(Level.FINE, () -> "Received '" + fileReference + "', which was not requested. Can be ignored if happening during upgrades/restarts");
        }
    }

    void add(FileReferenceDownload fileReferenceDownload) {
        downloads.put(fileReferenceDownload.fileReference(), fileReferenceDownload);
        downloadStatuses.put(fileReferenceDownload.fileReference());
    }

    void remove(FileReference fileReference) {
        downloadStatuses.get(fileReference).ifPresent(d -> new DownloadStatus(d.fileReference(), 0.0));
        downloads.remove(fileReference);
    }

    double downloadStatus(FileReference fileReference) {
        double status = 0.0;
        Optional<Downloads.DownloadStatus> downloadStatus = downloadStatuses.get(fileReference);
        if (downloadStatus.isPresent()) {
            status = downloadStatus.get().progress();
        }
        return status;
    }

    Map<FileReference, Double> downloadStatus() {
        return downloadStatuses.all().values().stream().collect(Collectors.toMap(Downloads.DownloadStatus::fileReference, Downloads.DownloadStatus::progress));
    }

    Optional<FileReferenceDownload> get(FileReference fileReference) {
        return Optional.ofNullable(downloads.get(fileReference));
    }

    /* Status for ongoing and completed downloads, keeps at most status for 100 last downloads */
    static class DownloadStatuses {

        private static final int maxEntries = 100;

        private final Map<FileReference, DownloadStatus> downloadStatus = Collections.synchronizedMap(new HashMap<>());

        void put(FileReference fileReference) {
            put(fileReference, 0.0);
        }

        void put(FileReference fileReference, double progress) {
            downloadStatus.put(fileReference, new DownloadStatus(fileReference, progress));
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

        @Override
        public String toString() {
            return downloadStatus.entrySet().stream().map(entry -> entry.getKey().value() + "=>" + entry.getValue().progress).collect(Collectors.joining(", "));
        }

    }

    static class DownloadStatus {
        private final FileReference fileReference;
        private final double progress; // between 0 and 1
        private final Instant created;

        DownloadStatus(FileReference fileReference, double progress) {
            this.fileReference = fileReference;
            this.progress = progress;
            this.created = Instant.now();
        }

        public FileReference fileReference() {
            return fileReference;
        }

        public double progress() {
            return progress;
        }

        public Instant created() {
            return created;
        }
    }

}
