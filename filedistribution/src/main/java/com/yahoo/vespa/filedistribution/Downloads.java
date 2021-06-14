package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class Downloads {
    private final Map<FileReference, FileReferenceDownload> downloads = new ConcurrentHashMap<>();

    void add(FileReferenceDownload fileReferenceDownload) {
        downloads.put(fileReferenceDownload.fileReference(), fileReferenceDownload);
    }

    void remove(FileReference fileReference) {
        downloads.remove(fileReference);
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
