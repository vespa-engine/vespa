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
import com.yahoo.vespa.filedistribution.EmptyFileReferenceData;
import com.yahoo.vespa.filedistribution.FileDistributionConnectionPool;
import com.yahoo.vespa.filedistribution.FileDownloader;
import com.yahoo.vespa.filedistribution.FileReferenceCompressor;
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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.server.filedistribution.FileDistributionUtil.getOtherConfigServersInCluster;
import static com.yahoo.vespa.filedistribution.FileReferenceData.CompressionType;
import static com.yahoo.vespa.filedistribution.FileReferenceData.CompressionType.gzip;
import static com.yahoo.vespa.filedistribution.FileReferenceData.Type;
import static com.yahoo.vespa.filedistribution.FileReferenceData.Type.compressed;

public class FileServer {

    private static final Logger log = Logger.getLogger(FileServer.class.getName());

    // Set this low, to make sure we don't wait for a long time trying to download file
    private static final Duration timeout = Duration.ofSeconds(10);
    private static final List<CompressionType> compressionTypesToServe = compressionTypesAsList(List.of("zstd", "lz4", "gzip")); // In preferred order

    private final FileDirectory fileDirectory;
    private final ExecutorService executor;
    private final FileDownloader downloader;
    private final List<CompressionType> compressionTypes; // compression types to use, in preferred order

    // TODO: Move to filedistribution module, so that it can be used by both clients and servers
    private enum FileApiErrorCodes {
        OK(0, "OK"),
        NOT_FOUND(1, "File reference not found"),
        TIMEOUT(2, "Timeout");
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
        ReplayStatus(int code, String description) {
            this.code = code;
            this.description = description;
        }
        public boolean ok() { return code == 0; }
        public int getCode() { return code; }
        public String getDescription() { return description; }
    }

    public interface Receiver {
        void receive(FileReferenceData fileData, ReplayStatus status);
    }

    @SuppressWarnings("WeakerAccess") // Created by dependency injection
    @Inject
    public FileServer(ConfigserverConfig configserverConfig, FileDirectory fileDirectory) {
        this(createFileDownloader(getOtherConfigServersInCluster(configserverConfig)), compressionTypesToServe, fileDirectory);
    }

    FileServer(FileDownloader fileDownloader, List<CompressionType> compressionTypes, FileDirectory fileDirectory) {
        this.downloader = fileDownloader;
        this.fileDirectory = fileDirectory;
        this.executor = Executors.newFixedThreadPool(Math.max(8, Runtime.getRuntime().availableProcessors()),
                                                     new DaemonThreadFactory("file-server-"));
        this.compressionTypes = compressionTypes;
    }

    boolean hasFile(String fileReference) {
        return hasFile(new FileReference(fileReference));
    }

    private boolean hasFile(FileReference reference) {
        try {
            return fileDirectory.getFile(reference).exists();
        } catch (IllegalArgumentException e) {
            log.log(Level.FINE, () -> "Failed locating " + reference + ": " + e.getMessage());
        }
        return false;
    }

    FileDirectory getRootDir() { return fileDirectory; }

    void startFileServing(FileReference reference, Receiver target, Set<CompressionType> acceptedCompressionTypes) {
        if ( ! fileDirectory.getFile(reference).exists()) return;

        File file = this.fileDirectory.getFile(reference);
        log.log(Level.FINE, () -> "Start serving " + reference + " with file '" + file.getAbsolutePath() + "'");
        FileReferenceData fileData = EmptyFileReferenceData.empty(reference, file.getName());
        try {
            fileData = readFileReferenceData(reference, acceptedCompressionTypes);
            target.receive(fileData, new ReplayStatus(0, "OK"));
            log.log(Level.FINE, () -> "Done serving " + reference.value() + " with file '" + file.getAbsolutePath() + "'");
        } catch (IOException e) {
            String errorDescription = "For" + reference.value() + ": failed reading file '" + file.getAbsolutePath() + "'";
            log.warning(errorDescription + " for sending to '" + target.toString() + "'. " + e.getMessage());
            target.receive(fileData, new ReplayStatus(1, errorDescription));
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed serving " + reference + ": " + Exceptions.toMessageString(e));
        } finally {
            fileData.close();
        }
    }

    private FileReferenceData readFileReferenceData(FileReference reference, Set<CompressionType> acceptedCompressionTypes) throws IOException {
        File file = this.fileDirectory.getFile(reference);

        if (file.isDirectory()) {
            Path tempFile = Files.createTempFile("filereferencedata", reference.value());
            CompressionType compressionType = chooseCompressionType(acceptedCompressionTypes);
            log.log(Level.FINE, () -> "accepted compression types=" + acceptedCompressionTypes + ", compression type to use=" + compressionType);
            File compressedFile = new FileReferenceCompressor(compressed, compressionType).compress(file.getParentFile(), tempFile.toFile());
            return new LazyTemporaryStorageFileReferenceData(reference, file.getName(), compressed, compressedFile, compressionType);
        } else {
            return new LazyFileReferenceData(reference, file.getName(), Type.file, file, gzip);
        }
    }

    public void serveFile(FileReference fileReference,
                          boolean downloadFromOtherSourceIfNotFound,
                          Set<CompressionType> acceptedCompressionTypes,
                          Request request, Receiver receiver) {
        log.log(Level.FINE, () -> "Received request for file reference '" + fileReference + "' from " + request.target());
        Instant deadline = Instant.now().plus(timeout);
        String client = request.target().toString();
        executor.execute(() -> {
            var result = serveFileInternal(fileReference, downloadFromOtherSourceIfNotFound, client, receiver, deadline, acceptedCompressionTypes);
            request.returnValues()
                   .add(new Int32Value(result.getCode()))
                   .add(new StringValue(result.getDescription()));
            request.returnRequest();
        });
    }

    private FileApiErrorCodes serveFileInternal(FileReference fileReference,
                                                boolean downloadFromOtherSourceIfNotFound,
                                                String client,
                                                Receiver receiver,
                                                Instant deadline,
                                                Set<CompressionType> acceptedCompressionTypes) {
        if (Instant.now().isAfter(deadline)) {
            log.log(Level.INFO, () -> "Deadline exceeded for request for file reference '" + fileReference + "' from " + client);
            return FileApiErrorCodes.TIMEOUT;
        }

        boolean fileExists;
        try {
            var fileReferenceDownload = new FileReferenceDownload(fileReference, client, downloadFromOtherSourceIfNotFound);
            fileExists = hasFileDownloadIfNeeded(fileReferenceDownload);
            if (fileExists) startFileServing(fileReference, receiver, acceptedCompressionTypes);
        } catch (IllegalArgumentException e) {
            fileExists = false;
            log.warning("Failed serving file reference '" + fileReference + "', request from " + client + " failed with: " + e.getMessage());
        }

        return (fileExists ? FileApiErrorCodes.OK : FileApiErrorCodes.NOT_FOUND);
    }

    /* Choose the first compression type (list is in preferred order) that matches an accepted compression type, or fail */
    private CompressionType chooseCompressionType(Set<CompressionType> acceptedCompressionTypes) {
        for (CompressionType compressionType : compressionTypes) {
            if (acceptedCompressionTypes.contains(compressionType))
                return compressionType;
        }
        throw new RuntimeException("Could not find a compression type that can be used. Accepted compression types: " +
                                           acceptedCompressionTypes + ", compression types server can use: " + compressionTypes);
    }

    boolean hasFileDownloadIfNeeded(FileReferenceDownload fileReferenceDownload) {
        FileReference fileReference = fileReferenceDownload.fileReference();
        if (hasFile(fileReference)) return true;

        if (fileReferenceDownload.downloadFromOtherSourceIfNotFound()) {
            log.log(Level.FINE, "File not found, downloading from another source");
            // Create new FileReferenceDownload with downloadFromOtherSourceIfNotFound set to false
            // to avoid requesting a file reference perpetually, e.g. for a file that does not exist anymore
            FileReferenceDownload newDownload = new FileReferenceDownload(fileReference,
                                                                          fileReferenceDownload.client(),
                                                                          false);
            boolean fileExists = downloader.getFile(newDownload).isPresent();
            if ( ! fileExists)
                log.log(Level.INFO, "Failed downloading '" + fileReferenceDownload + "'");
            return fileExists;
        } else {
            log.log(Level.FINE, "File not found, will not download from another source");
            return false;
        }
    }

    public FileDownloader downloader() { return downloader; }

    public void close() {
        downloader.close();
        executor.shutdown();
    }

    private static FileDownloader createFileDownloader(List<String> configServers) {
        Supervisor supervisor = new Supervisor(new Transport("filedistribution-pool")).setDropEmptyBuffers(true);
        return new FileDownloader(createConnectionPool(configServers, supervisor), supervisor, timeout);
    }

    private static List<CompressionType> compressionTypesAsList(List<String> compressionTypes) {
        return compressionTypes.stream()
                               .map(CompressionType::valueOf)
                               .toList();
    }

    private static ConnectionPool createConnectionPool(List<String> configServers, Supervisor supervisor) {
        ConfigSourceSet configSourceSet = new ConfigSourceSet(configServers);
        if (configServers.size() == 0) return FileDownloader.emptyConnectionPool();

        return new FileDistributionConnectionPool(configSourceSet, supervisor);
    }

}
