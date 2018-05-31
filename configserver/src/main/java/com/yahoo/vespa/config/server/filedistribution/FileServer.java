// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.FileReference;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.log.LogLevel;
import com.yahoo.net.HostName;
import com.yahoo.vespa.config.Connection;
import com.yahoo.vespa.config.ConnectionPool;
import com.yahoo.vespa.config.JRTConnectionPool;
import com.yahoo.vespa.config.server.ConfigServerSpec;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.filedistribution.CompressedFileReference;
import com.yahoo.vespa.filedistribution.FileDownloader;
import com.yahoo.vespa.filedistribution.FileReferenceData;
import com.yahoo.vespa.filedistribution.FileReferenceDataBlob;
import com.yahoo.vespa.filedistribution.FileReferenceDownload;
import com.yahoo.vespa.filedistribution.LazyFileReferenceData;
import com.yahoo.yolean.Exceptions;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
        public ReplayStatus(int code, String description) {
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

    @Inject
    public FileServer(ConfigserverConfig configserverConfig) {
        this(createConnectionPool(configserverConfig), new File(Defaults.getDefaults().underVespaHome(configserverConfig.fileReferencesDir())));
    }

    // For testing only
    public FileServer(File rootDir) {
        this(new EmptyConnectionPool(), rootDir);
    }

    private FileServer(ConnectionPool connectionPool, File rootDir) {
        this.downloader = new FileDownloader(connectionPool);
        this.root = new FileDirectory(rootDir);
        this.pushExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.pullExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    public boolean hasFile(String fileName) {
        return hasFile(new FileReference(fileName));
    }

    private boolean hasFile(FileReference reference) {
        try {
            return root.getFile(reference).exists();
        } catch (IllegalArgumentException e) {
            log.log(LogLevel.DEBUG, "Failed locating file reference '" + reference + "' with error " + e.toString());
        }
        return false;
    }
    public void startFileServing(String fileName, Receiver target) {
        FileReference reference = new FileReference(fileName);
        File file = root.getFile(reference);

        if (file.exists()) {
            pushExecutor.execute(() -> serveFile(reference, target));
        }
    }

    private void serveFile(FileReference reference, Receiver target) {
        File file = root.getFile(reference);
        log.log(LogLevel.DEBUG, () -> "Start serving reference '" + reference.value() + "' with file '" + file.getAbsolutePath() + "'");
        boolean success = false;
        String errorDescription = "OK";
        FileReferenceData fileData = FileReferenceDataBlob.empty(reference, file.getName());
        try {
            fileData = readFileReferenceData(reference);
            success = true;
        } catch (IOException e) {
            errorDescription = "For file reference '" + reference.value() + "': failed reading file '" + file.getAbsolutePath() + "'";
            log.warning(errorDescription + " for sending to '" + target.toString() + "'. " + e.toString());
        }

        try {
            target.receive(fileData, new ReplayStatus(success ? 0 : 1, success ? "OK" : errorDescription));
            log.log(LogLevel.DEBUG, "Done serving file reference '" + reference.value() + "' with file '" + file.getAbsolutePath() + "'");
        } catch (Exception e) {
            log.log(LogLevel.WARNING, "Failed serving file reference '" + reference.value() + "': " + Exceptions.toMessageString(e));
        } finally {
            fileData.close();
        }
    }

    private FileReferenceData readFileReferenceData(FileReference reference) throws IOException {
        File file = root.getFile(reference);

        if (file.isDirectory()) {
            //TODO Here we should compress to file, but then we have to clean up too. Pending.
            byte [] blob = CompressedFileReference.compress(file.getParentFile());
            return new FileReferenceDataBlob(reference, file.getName(), FileReferenceData.Type.compressed, blob);
        } else {
            return new LazyFileReferenceData(reference, file.getName(), FileReferenceData.Type.file, file);
        }
    }
    public void serveFile(Request request, Receiver receiver) {
        pullExecutor.execute(() -> serveFile(request.parameters().get(0).asString(), request, receiver));
    }
    private void serveFile(String fileReference, Request request, Receiver receiver) {
        FileApiErrorCodes result;
        try {
            log.log(LogLevel.DEBUG, () -> "Received request for reference '" + fileReference + "' from " + request.target());
            result = hasFile(fileReference)
                    ? FileApiErrorCodes.OK
                    : FileApiErrorCodes.NOT_FOUND;
            if (result == FileApiErrorCodes.OK) {
                startFileServing(fileReference, receiver);
            } else {
                // Non-zero second parameter means that the request should never lead
                // to a new download typically because the request comes from another config server.
                // This is to avoid config servers asking each other for a file that does not exist
                if (request.parameters().size() == 1 || request.parameters().get(1).asInt32() == 0) {
                    log.log(LogLevel.DEBUG, "File not found, downloading from another source");
                    downloader.getFile(new FileReferenceDownload(new FileReference(fileReference), false /* downloadFromOtherSourceIfNotFound */));
                } else {
                    log.log(LogLevel.DEBUG, "File not found, will not download from another source since request came from another config server");
                    result = FileApiErrorCodes.NOT_FOUND;
                }
            }
        } catch (IllegalArgumentException e) {
            result = FileApiErrorCodes.NOT_FOUND;
            log.warning("Failed serving file reference '" + fileReference + "', request was from " + request.target() + ", with error " + e.toString());
        }
        request.returnValues()
                .add(new Int32Value(result.getCode()))
                .add(new StringValue(result.getDescription()));
        request.returnRequest();
    }

    public FileDownloader downloader() {
        return downloader;
    }

    // Connection pool with all config servers except this one (might be an empty pool if there is only one config server)
    private static ConnectionPool createConnectionPool(ConfigserverConfig configserverConfig) {
        List<String> configServers = ConfigServerSpec.fromConfig(configserverConfig)
                .stream()
                .filter(spec -> !spec.getHostName().equals(HostName.getLocalhost()))
                .map(spec -> "tcp/" + spec.getHostName() + ":" + spec.getConfigServerPort())
                .collect(Collectors.toList());

        return configServers.size() > 0 ? new JRTConnectionPool(new ConfigSourceSet(configServers)) : new EmptyConnectionPool();
    }

    private static class EmptyConnectionPool implements ConnectionPool {

        @Override
        public void close() {}

        @Override
        public void setError(Connection connection, int i) {}

        @Override
        public Connection getCurrent() { return null; }

        @Override
        public Connection setNewCurrentConnection() { return null; }

        @Override
        public int getSize() { return 0; }

        @Override
        public Supervisor getSupervisor() { return new Supervisor(new Transport()); }
    }
}
