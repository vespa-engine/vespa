// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.vespa.filedistribution.FileApiErrorCodes;
import com.yahoo.vespa.filedistribution.FileDistributionConnectionPool;
import com.yahoo.vespa.filedistribution.FileDownloader;
import com.yahoo.vespa.filedistribution.FileReferenceCompressor;
import com.yahoo.vespa.filedistribution.FileReferenceData;
import com.yahoo.vespa.filedistribution.FileReferenceDownload;
import com.yahoo.vespa.filedistribution.LazyFileReferenceData;
import com.yahoo.vespa.filedistribution.LazyTemporaryStorageFileReferenceData;
import com.yahoo.vespa.flags.FlagSource;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.server.filedistribution.FileDistributionUtil.getOtherConfigServersInCluster;
import static com.yahoo.vespa.filedistribution.FileApiErrorCodes.NOT_FOUND;
import static com.yahoo.vespa.filedistribution.FileApiErrorCodes.OK;
import static com.yahoo.vespa.filedistribution.FileApiErrorCodes.TRANSFER_FAILED;
import static com.yahoo.vespa.filedistribution.FileReferenceData.CompressionType;
import static com.yahoo.vespa.filedistribution.FileReferenceData.CompressionType.gzip;
import static com.yahoo.vespa.filedistribution.FileReferenceData.CompressionType.lz4;
import static com.yahoo.vespa.filedistribution.FileReferenceData.CompressionType.none;
import static com.yahoo.vespa.filedistribution.FileReferenceData.CompressionType.zstd;
import static com.yahoo.vespa.filedistribution.FileReferenceData.Type;
import static com.yahoo.vespa.filedistribution.FileReferenceData.Type.compressed;
import static com.yahoo.yolean.Exceptions.uncheck;
import static java.util.logging.Level.FINE;

public class FileServer {

    private static final Logger log = Logger.getLogger(FileServer.class.getName());

    private static final Duration timeout = Duration.ofSeconds(60);
    /* In preferred order, the one used will be the first one matching one of the accepted compression
     * types sent in client request.
     */
    private static final List<CompressionType> compressionTypesToServe = List.of(zstd, lz4, gzip, none);
    private static final String tempFilereferencedataPrefix = "filereferencedata";
    private static final Path tempFilereferencedataDir = Paths.get(System.getProperty("java.io.tmpdir"));

    private final FileDirectory fileDirectory;
    private final ThreadPoolExecutor executor;
    private final FileDownloader downloader; // downloads files from other config servers
    private final List<CompressionType> compressionTypes; // compression types to use, in preferred order

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
    public FileServer(ConfigserverConfig configserverConfig, FlagSource flagSource, FileDirectory fileDirectory) {
        this(createFileDownloader(getOtherConfigServersInCluster(configserverConfig)),
             compressionTypesToServe,
             fileDirectory);
        // Clean up temporary files from previous runs (e.g. if JVM was killed)
        try (var files = uncheck(() -> Files.list(tempFilereferencedataDir))) {
            files.filter(path -> path.toFile().isFile())
                  .filter(path -> path.toFile().getName().startsWith(tempFilereferencedataPrefix))
                  .forEach(path -> uncheck(() -> Files.delete(path)));
        }
    }

    FileServer(FileDownloader fileDownloader, List<CompressionType> compressionTypes, FileDirectory fileDirectory) {
        this.downloader = fileDownloader;
        this.fileDirectory = fileDirectory;
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(Math.max(8, Runtime.getRuntime().availableProcessors()),
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

        log.log(FINE, () -> "Failed locating " + reference);
        return false;
    }

    FileDirectory getRootDir() { return fileDirectory; }

    void startFileServing(FileReference reference, File file, Receiver target, Set<CompressionType> acceptedCompressionTypes) {
        var absolutePath = file.getAbsolutePath();
        try (FileReferenceData fileData = fileReferenceData(reference, acceptedCompressionTypes, file)) {
            log.log(FINE, () -> "Start serving " + reference.value() + " with file '" + absolutePath + "'");
            target.receive(fileData, new ReplayStatus(0, "OK"));
            log.log(FINE, () -> "Done serving " + reference.value() + " with file '" + absolutePath + "'");
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
        CompressionType compressionType = chooseCompressionType(acceptedCompressionTypes);
        log.log(Level.FINE, () -> "accepted compression types: " + acceptedCompressionTypes + ", will use " + compressionType);
        if (file.isDirectory()) {
            Path tempFile = Files.createTempFile(tempFilereferencedataDir, tempFilereferencedataPrefix, reference.value());
            var start = Instant.now();
            File compressedFile = new FileReferenceCompressor(compressed, compressionType)
                    .compress(file.getParentFile(), tempFile.toFile());
            var duration = Duration.between(start, Instant.now());
            log.log((duration.compareTo(Duration.ofSeconds(10)) > 0) ? Level.INFO : Level.FINE,
                    () -> "compressed " + reference + " with " + compressionType + " in " + Duration.between(start, Instant.now()));
            return new LazyTemporaryStorageFileReferenceData(reference, file.getName(), compressed, compressedFile, compressionType);
        } else {
            return new LazyFileReferenceData(reference, file.getName(), Type.file, file, compressionType);
        }
    }

    public void serveFile(FileReference fileReference,
                          boolean downloadFromOtherSourceIfNotFound,
                          Set<CompressionType> acceptedCompressionTypes,
                          Request request,
                          Receiver receiver) {
        log.log(FINE, () -> "Received request for " + fileReference + " from " + request.target().peerSpec().host() +
                ", download from other source: " + downloadFromOtherSourceIfNotFound);
        String client = request.target().toString();
        log.log(FINE, executor.getActiveCount() + " out of " + executor.getMaximumPoolSize() + " threads are active");
        executor.execute(() -> {
            var result = serveFileInternal(fileReference, downloadFromOtherSourceIfNotFound, client, receiver, acceptedCompressionTypes);
            request.returnValues()
                   .add(new Int32Value(result.code()))
                   .add(new StringValue(result.description()));
            log.log(FINE, () -> "Returning request for " + fileReference + " from " + request.target());
            request.returnRequest();
        });
    }

    private FileApiErrorCodes serveFileInternal(FileReference fileReference,
                                                boolean downloadFromOtherSourceIfNotFound,
                                                String client,
                                                Receiver receiver,
                                                Set<CompressionType> acceptedCompressionTypes) {
        try {
            var fileReferenceDownload = new FileReferenceDownload(fileReference, client, downloadFromOtherSourceIfNotFound);
            var file = getFileDownloadIfNeeded(fileReferenceDownload);
            if (file.isEmpty()) return NOT_FOUND;

            startFileServing(fileReference, file.get(), receiver, acceptedCompressionTypes);
        } catch (Exception e) {
            log.warning("Failed serving " + fileReference + ", request from " + client + " failed with: " + e.getMessage());
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
            log.log(FINE, fileReferenceDownload + " not found, downloading from another source");
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
            log.log(FINE, "File not found, will not download from another source");
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

    private static ConnectionPool createConnectionPool(List<String> configServers, Supervisor supervisor) {
        if (configServers.isEmpty()) return FileDownloader.emptyConnectionPool();

        return new FileDistributionConnectionPool(new ConfigSourceSet(configServers), supervisor);
    }

}
