// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;

import java.io.File;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
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
        Optional<Downloads.DownloadStatus> downloadStatus = downloadStatuses.get(fileReference);
        if (downloadStatus.isPresent())
            downloadStatus.get().setProgress(completeness);
        else
            downloadStatuses.add(fileReference, completeness);
    }

    void completedDownloading(FileReference fileReference, File file) {
        Optional<FileReferenceDownload> download = get(fileReference);
        if (download.isPresent()) {
            downloadStatuses().get(fileReference).ifPresent(Downloads.DownloadStatus::finished);
            downloads.remove(fileReference);
            download.get().future().complete(Optional.of(file));
        } else {
            log.log(Level.FINE, () -> "Received '" + fileReference + "', which was not requested. Can be ignored if happening during upgrades/restarts");
        }
    }

    void add(FileReferenceDownload fileReferenceDownload) {
        downloads.put(fileReferenceDownload.fileReference(), fileReferenceDownload);
        downloadStatuses.add(fileReferenceDownload.fileReference());
    }

    void remove(FileReference fileReference) {
        downloadStatuses.get(fileReference).ifPresent(d -> d.setProgress(0.0));
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

        private final Map<FileReference, DownloadStatus> downloadStatus = new ConcurrentHashMap<>();

        void add(FileReference fileReference) {
            add(fileReference, 0.0);
        }

        void add(FileReference fileReference, double progress) {
            DownloadStatus ds = new DownloadStatus(fileReference);
            ds.setProgress(progress);
            downloadStatus.put(fileReference, ds);
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

    }

    static class DownloadStatus {
        private final FileReference fileReference;
        private double progress; // between 0 and 1
        private final Instant created;

        DownloadStatus(FileReference fileReference) {
            this.fileReference = fileReference;
            this.progress = 0.0;
            this.created = Instant.now();
        }

        public FileReference fileReference() {
            return fileReference;
        }

        public double progress() {
            return progress;
        }

        public void setProgress(double progress) {
            this.progress = progress;
        }

        public void finished() {
            setProgress(1.0);
        }

        public Instant created() {
            return created;
        }
    }

}
