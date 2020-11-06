// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.FileReference;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.StringValue;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.filedistribution.CompressedFileReference;
import com.yahoo.vespa.filedistribution.FileDownloader;
import com.yahoo.vespa.filedistribution.FileReferenceData;
import com.yahoo.vespa.filedistribution.EmptyFileReferenceData;
import com.yahoo.vespa.filedistribution.FileReferenceDownload;
import com.yahoo.vespa.filedistribution.LazyFileReferenceData;
import com.yahoo.vespa.filedistribution.LazyTemporaryStorageFileReferenceData;
import com.yahoo.yolean.Exceptions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.server.filedistribution.FileDistributionUtil.createConnectionPool;
import static com.yahoo.vespa.config.server.filedistribution.FileDistributionUtil.emptyConnectionPool;

public class FileServer {
    private static final Logger log = Logger.getLogger(FileServer.class.getName());

    private final FileDirectory root;
    private final ExecutorService pushExecutor;
    private final ExecutorService pullExecutor;
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
    public FileServer(ConfigserverConfig configserverConfig) {
        this(new File(Defaults.getDefaults().underVespaHome(configserverConfig.fileReferencesDir())),
             new FileDownloader(createConnectionPool(configserverConfig)));
    }

    // For testing only
    public FileServer(File rootDir) {
        this(rootDir, new FileDownloader(emptyConnectionPool()));
    }

    public FileServer(File rootDir, FileDownloader fileDownloader) {
        this.downloader = fileDownloader;
        this.root = new FileDirectory(rootDir);
        this.pushExecutor = Executors.newFixedThreadPool(Math.max(8, Runtime.getRuntime().availableProcessors()),
                                                         new DaemonThreadFactory("file server push"));
        this.pullExecutor = Executors.newFixedThreadPool(Math.max(8, Runtime.getRuntime().availableProcessors()),
                                                         new DaemonThreadFactory("file server pull"));
    }

    boolean hasFile(String fileReference) {
        return hasFile(new FileReference(fileReference));
    }

    FileDirectory getRootDir() {
        return root;
    }

    private boolean hasFile(FileReference reference) {
        try {
            return root.getFile(reference).exists();
        } catch (IllegalArgumentException e) {
            log.log(Level.FINE, "Failed locating file reference '" + reference + "' with error " + e.toString());
        }
        return false;
    }

    void startFileServing(String fileName, Receiver target) {
        FileReference reference = new FileReference(fileName);
        File file = root.getFile(reference);

        if (file.exists()) {
            pushExecutor.execute(() -> serveFile(reference, target));
        }
    }

    private void serveFile(FileReference reference, Receiver target) {
        File file = root.getFile(reference);
        log.log(Level.FINE, () -> "Start serving reference '" + reference.value() + "' with file '" + file.getAbsolutePath() + "'");
        boolean success = false;
        String errorDescription = "OK";
        FileReferenceData fileData = EmptyFileReferenceData.empty(reference, file.getName());
        try {
            fileData = readFileReferenceData(reference);
            success = true;
        } catch (IOException e) {
            errorDescription = "For file reference '" + reference.value() + "': failed reading file '" + file.getAbsolutePath() + "'";
            log.warning(errorDescription + " for sending to '" + target.toString() + "'. " + e.toString());
        }

        try {
            target.receive(fileData, new ReplayStatus(success ? 0 : 1, success ? "OK" : errorDescription));
            log.log(Level.FINE, "Done serving file reference '" + reference.value() + "' with file '" + file.getAbsolutePath() + "'");
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed serving file reference '" + reference.value() + "': " + Exceptions.toMessageString(e));
        } finally {
            fileData.close();
        }
    }

    private FileReferenceData readFileReferenceData(FileReference reference) throws IOException {
        File file = root.getFile(reference);

        if (file.isDirectory()) {
            Path tempFile = Files.createTempFile("filereferencedata", reference.value());
            File compressedFile = CompressedFileReference.compress(file.getParentFile(), tempFile.toFile());
            return new LazyTemporaryStorageFileReferenceData(reference, file.getName(), FileReferenceData.Type.compressed, compressedFile);
        } else {
            return new LazyFileReferenceData(reference, file.getName(), FileReferenceData.Type.file, file);
        }
    }

    public void serveFile(String fileReference, boolean downloadFromOtherSourceIfNotFound, Request request, Receiver receiver) {
        pullExecutor.execute(() -> serveFileInternal(fileReference, downloadFromOtherSourceIfNotFound, request, receiver));
    }

    private void serveFileInternal(String fileReference, boolean downloadFromOtherSourceIfNotFound, Request request, Receiver receiver) {
        log.log(Level.FINE, () -> "Received request for reference '" + fileReference + "' from " + request.target());

        boolean fileExists;
        try {
            String client = request.target().toString();
            FileReferenceDownload fileReferenceDownload = new FileReferenceDownload(new FileReference(fileReference),
                                                                                    downloadFromOtherSourceIfNotFound,
                                                                                    client);
            fileExists = hasFileDownloadIfNeeded(fileReferenceDownload);
            if (fileExists) startFileServing(fileReference, receiver);
        } catch (IllegalArgumentException e) {
            fileExists = false;
            log.warning("Failed serving file reference '" + fileReference + "', request was from " + request.target() + ", with error " + e.toString());
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
            // to avoid config servers requesting a file reference perpetually, e.g. for a file that does not exist anymore
            FileReferenceDownload newDownload = new FileReferenceDownload(fileReference, false, fileReferenceDownload.client());
            return downloader.getFile(newDownload).isPresent();
        } else {
            log.log(Level.FINE, "File not found, will not download from another source since request came from another config server");
            return false;
        }
    }

    public FileDownloader downloader() {
        return downloader;
    }

    public void close() {
        downloader.close();
        pullExecutor.shutdown();
        pushExecutor.shutdown();
    }

}
