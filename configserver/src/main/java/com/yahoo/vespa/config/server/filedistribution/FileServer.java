// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.FileReference;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.vespa.config.ConnectionPool;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.filedistribution.FileReferenceCompressor;
import com.yahoo.vespa.filedistribution.EmptyFileReferenceData;
import com.yahoo.vespa.filedistribution.FileDistributionConnectionPool;
import com.yahoo.vespa.filedistribution.FileDownloader;
import com.yahoo.vespa.filedistribution.FileReferenceData;
import com.yahoo.vespa.filedistribution.FileReferenceDownload;
import com.yahoo.vespa.filedistribution.LazyFileReferenceData;
import com.yahoo.vespa.filedistribution.LazyTemporaryStorageFileReferenceData;
import com.yahoo.yolean.Exceptions;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.server.filedistribution.FileDistributionUtil.getOtherConfigServersInCluster;
import static com.yahoo.vespa.filedistribution.FileReferenceData.Type.compressed;

public class FileServer {

    private static final Logger log = Logger.getLogger(FileServer.class.getName());

    // Set this low, to make sure we don't wait for a long time trying to download file
    private static final Duration timeout = Duration.ofSeconds(10);

    private final FileDirectory root;
    private final ExecutorService executor;
    private final FileDownloader downloader;

    private enum FileApiErrorCodes {
        OK(0, "OK"),
        NOT_FOUND(1, "Filereference not found");
        private final int code;
        private final String description;
        FileApiErrorCodes(int code, String description) {
            this.code = code;
            this.description = description;
        }
        int getCode() { return code; }
        String getDescription() { return description; }
    }

    public static class ReplayStatus {
        private final int code;
        private final String description;
        private ReplayStatus(int code, String description) {
            this.code = code;
            this.description = description;
        }
        private static ReplayStatus ok() { return new ReplayStatus(0, "OK"); }
        private static ReplayStatus error(String errorMessage) { return new ReplayStatus(1, errorMessage); }
        public boolean isOk() { return code == 0; }
        public int getCode() { return code; }
        public String getDescription() { return description; }
    }

    public interface Receiver {
        void receive(FileReferenceData fileData, ReplayStatus status);
    }

    @SuppressWarnings("WeakerAccess") // Created by dependency injection
    @Inject
    public FileServer(ConfigserverConfig configserverConfig) {
        this(new File(Defaults.getDefaults().underVespaHome(configserverConfig.fileReferencesDir())),
             createFileDownloader(configserverConfig));
    }

    // For testing only
    public FileServer(File rootDir) {
        this(rootDir, createFileDownloader());
    }

    FileServer(File rootDir, FileDownloader fileDownloader) {
        this.downloader = fileDownloader;
        this.root = new FileDirectory(rootDir);
        this.executor = Executors.newFixedThreadPool(Math.max(8, Runtime.getRuntime().availableProcessors()),
                                                     new DaemonThreadFactory("file-server-"));
    }

    boolean hasFile(String fileReference) {
        return hasFile(new FileReference(fileReference));
    }

    private boolean hasFile(FileReference reference) {
        try {
            return root.getFile(reference).exists();
        } catch (IllegalArgumentException e) {
            log.log(Level.FINE, () -> "Failed locating " + reference + ": " + e.getMessage());
        }
        return false;
    }

    FileDirectory getRootDir() { return root; }

    void startFileServing(String fileName, Receiver target) {
        FileReference reference = new FileReference(fileName);
        File file = root.getFile(reference);

        if (file.exists()) {
            serveFile(reference, target);
        }
    }

    private void serveFile(FileReference reference, Receiver target) {
        File file = root.getFile(reference);
        log.log(Level.FINE, () -> "Start serving " + reference + " with file '" + file.getAbsolutePath() + "'");
        FileReferenceData fileData = EmptyFileReferenceData.empty(reference, file.getName());
        try {
            fileData = readFileReferenceData(reference);
            target.receive(fileData, ReplayStatus.ok());
            log.log(Level.FINE, () -> "Done serving " + reference.value() + " with file '" + file.getAbsolutePath() + "'");
        } catch (IOException e) {
            String errorDescription = "For" + reference.value() + ": failed reading file '" + file.getAbsolutePath() + "'";
            log.warning(errorDescription + " for sending to '" + target.toString() + "'. " + e.getMessage());
            target.receive(fileData, ReplayStatus.error(errorDescription));
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed serving " + reference + ": " + Exceptions.toMessageString(e));
        } finally {
            fileData.close();
        }
    }

    private FileReferenceData readFileReferenceData(FileReference reference) throws IOException {
        File file = root.getFile(reference);

        if (file.isDirectory()) {
            Path tempFile = Files.createTempFile("filereferencedata", reference.value());
            File compressedFile = new FileReferenceCompressor(compressed).compress(file.getParentFile(), tempFile.toFile());
            return new LazyTemporaryStorageFileReferenceData(reference, file.getName(), compressed, compressedFile);
        } else {
            return new LazyFileReferenceData(reference, file.getName(), FileReferenceData.Type.file, file);
        }
    }

    public void serveFile(String fileReference, boolean downloadFromOtherSourceIfNotFound, Request request, Receiver receiver) {
        if (executor instanceof ThreadPoolExecutor)
            log.log(Level.FINE, () -> "Active threads: " + ((ThreadPoolExecutor) executor).getActiveCount());

        log.log(Level.FINE, () -> "Received request for file reference '" + fileReference + "' from " + request.target());
        Instant deadline = Instant.now().plus(timeout);
        executor.execute(() -> serveFileInternal(fileReference, downloadFromOtherSourceIfNotFound, request, receiver, deadline));
    }

    private void serveFileInternal(String fileReference,
                                   boolean downloadFromOtherSourceIfNotFound,
                                   Request request,
                                   Receiver receiver,
                                   Instant deadline) {
        if (Instant.now().isAfter(deadline)) {
            log.log(Level.INFO, () -> "Deadline exceeded for request for file reference '" + fileReference + "' from " + request.target() +
                    " , giving up");
            return;
        }

        boolean fileExists;
        try {
            String client = request.target().toString();
            FileReferenceDownload fileReferenceDownload = new FileReferenceDownload(new FileReference(fileReference),
                                                                                    client,
                                                                                    downloadFromOtherSourceIfNotFound);
            fileExists = hasFileDownloadIfNeeded(fileReferenceDownload);
            if (fileExists)
                startFileServing(fileReference, receiver);
        } catch (IllegalArgumentException e) {
            fileExists = false;
            log.warning("Failed serving file reference '" + fileReference + "', request was from " + request.target() + ", with error " + e.getMessage());
        }

        FileApiErrorCodes result = fileExists ? FileApiErrorCodes.OK : FileApiErrorCodes.NOT_FOUND;
        request.returnValues()
                .add(new Int32Value(result.getCode()))
                .add(new StringValue(result.getDescription()));
        request.returnRequest();
    }

    boolean hasFileDownloadIfNeeded(FileReferenceDownload fileReferenceDownload) {
        FileReference fileReference = fileReferenceDownload.fileReference();
        if (hasFile(fileReference)) return true;

        if (fileReferenceDownload.downloadFromOtherSourceIfNotFound()) {
            log.log(Level.FINE, "File not found, downloading from another source");
            // Create new FileReferenceDownload with downloadFromOtherSourceIfNotFound set to false
            // to avoid config servers requesting a file reference perpetually, e.g. for a file that
            // does not exist anymore
            FileReferenceDownload newDownload = new FileReferenceDownload(fileReference,
                                                                          fileReferenceDownload.client(),
                                                                          false);
            boolean fileExists = downloader.getFile(newDownload).isPresent();
            if ( ! fileExists)
                log.log(Level.WARNING, "Failed downloading '" + fileReferenceDownload + "'");
            return fileExists;
        } else {
            log.log(Level.FINE, "File not found, will not download from another source, since request came from another config server");
            return false;
        }
    }

    public FileDownloader downloader() { return downloader; }

    public void close() {
        downloader.close();
        executor.shutdown();
    }

    private static FileDownloader createFileDownloader() {
        return createFileDownloader(List.of());
    }

    private static FileDownloader createFileDownloader(ConfigserverConfig configserverConfig) {
        return createFileDownloader(getOtherConfigServersInCluster(configserverConfig));
    }

    private static FileDownloader createFileDownloader(List<String> configServers) {
        Supervisor supervisor = new Supervisor(new Transport("filedistribution-pool")).setDropEmptyBuffers(true);
        return new FileDownloader(configServers.isEmpty()
                                          ? FileDownloader.emptyConnectionPool()
                                          : createConnectionPool(configServers, supervisor),
                                  supervisor,
                                  timeout);
    }

    private static ConnectionPool createConnectionPool(List<String> configServers, Supervisor supervisor) {
        @SuppressWarnings("removal") // TODO Vespa 8: remove
        ConfigSourceSet configSourceSet = new ConfigSourceSet(configServers);
        if (configServers.size() == 0) return FileDownloader.emptyConnectionPool();

        return new FileDistributionConnectionPool(configSourceSet, supervisor);
    }

}
