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
import com.yahoo.vespa.filedistribution.FileDistributionConnectionPool;
import com.yahoo.vespa.filedistribution.FileDownloader;
import com.yahoo.vespa.filedistribution.FileReferenceCompressor;
import com.yahoo.vespa.filedistribution.FileReferenceData;
import com.yahoo.vespa.filedistribution.FileReferenceDownload;
import com.yahoo.vespa.filedistribution.LazyFileReferenceData;
import com.yahoo.vespa.filedistribution.LazyTemporaryStorageFileReferenceData;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.server.filedistribution.FileDistributionUtil.getOtherConfigServersInCluster;
import static com.yahoo.vespa.config.server.filedistribution.FileServer.FileApiErrorCodes.NOT_FOUND;
import static com.yahoo.vespa.config.server.filedistribution.FileServer.FileApiErrorCodes.OK;
import static com.yahoo.vespa.config.server.filedistribution.FileServer.FileApiErrorCodes.TIMEOUT;
import static com.yahoo.vespa.config.server.filedistribution.FileServer.FileApiErrorCodes.TRANSFER_FAILED;
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
    enum FileApiErrorCodes {
        OK(0, "OK"),
        NOT_FOUND(1, "File reference not found"),
        TIMEOUT(2, "Timeout"),
        TRANSFER_FAILED(3, "Failed transferring file");
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
        Optional<File> file = fileDirectory.getFile(reference);
        if (file.isPresent())
            return file.get().exists();

        log.log(Level.FINE, () -> "Failed locating " + reference);
        return false;
    }

    FileDirectory getRootDir() { return fileDirectory; }

    void startFileServing(FileReference reference, File file, Receiver target, Set<CompressionType> acceptedCompressionTypes) {
        var absolutePath = file.getAbsolutePath();
        try (FileReferenceData fileData = fileReferenceData(reference, acceptedCompressionTypes, file)) {
            log.log(Level.FINE, () -> "Start serving " + reference.value() + " with file '" + absolutePath + "'");
            target.receive(fileData, new ReplayStatus(0, "OK"));
            log.log(Level.FINE, () -> "Done serving " + reference.value() + " with file '" + absolutePath + "'");
        } catch (IOException ioe) {
            throw new UncheckedIOException("For " + reference.value() + ": failed reading file '" + absolutePath + "'" +
                                           " for sending to '" + target.toString() + "'. ", ioe);
        } catch (Exception e) {
            throw new RuntimeException("Failed serving " + reference.value() + " to '" + target + "': ", e);
        }
    }

    private FileReferenceData fileReferenceData(FileReference reference,
                                                Set<CompressionType> acceptedCompressionTypes,
                                                File file) throws IOException {
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
            return TIMEOUT;
        }

        try {
            var fileReferenceDownload = new FileReferenceDownload(fileReference, client, downloadFromOtherSourceIfNotFound);
            var file = getFileDownloadIfNeeded(fileReferenceDownload);
            if (file.isEmpty()) return NOT_FOUND;

            startFileServing(fileReference, file.get(), receiver, acceptedCompressionTypes);
        } catch (Exception e) {
            log.warning("Failed serving file reference '" + fileReference + "', request from " + client + " failed with: " + e.getMessage());
            return TRANSFER_FAILED;
        }

        return OK;
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

    public Optional<File> getFileDownloadIfNeeded(FileReferenceDownload fileReferenceDownload) {
        FileReference fileReference = fileReferenceDownload.fileReference();
        Optional<File> file = fileDirectory.getFile(fileReference);
        if (file.isPresent())
            return file;

        if (fileReferenceDownload.downloadFromOtherSourceIfNotFound()) {
            log.log(Level.FINE, "File not found, downloading from another source");
            // Create new FileReferenceDownload with downloadFromOtherSourceIfNotFound set to false
            // to avoid requesting a file reference perpetually, e.g. for a file that does not exist anymore
            FileReferenceDownload newDownload = new FileReferenceDownload(fileReference,
                                                                          fileReferenceDownload.client(),
                                                                          false);
            file = downloader.getFile(newDownload);
            if (file.isEmpty())
                log.log(Level.INFO, "Failed downloading '" + fileReferenceDownload + "'");
            return file;
        } else {
            log.log(Level.FINE, "File not found, will not download from another source");
            return Optional.empty();
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
