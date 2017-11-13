// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.google.inject.Inject;
import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class FileServer {
    private static final Logger log = Logger.getLogger(FileServer.class.getName());
    private final FileDirectory root;
    private final ExecutorService executor;

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
        void receive(FileReference reference, String filename, byte [] content, ReplayStatus status);
    }

    @Inject
    public FileServer() {
        this(FileDistribution.getDefaultFileDBPath());
    }

    public FileServer(File rootDir) {
        this(rootDir, Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
    }

    public FileServer(File rootDir, ExecutorService executor) {
        this.root = new FileDirectory(rootDir);
        this.executor = executor;
    }
    public boolean hasFile(String fileName) {
        return hasFile(new FileReference(fileName));
    }
    public boolean hasFile(FileReference reference) {
        try {
            return root.getFile(reference).exists();
        } catch (IllegalArgumentException e) {
            log.warning("Failed locating file reference '" + reference + "' with error " + e.toString());
        }
        return false;
    }
    public boolean startFileServing(String fileName, Receiver target) {
        FileReference reference = new FileReference(fileName);
        File file = root.getFile(reference);

        if (file.exists()) {
            executor.execute(() -> serveFile(reference, target));
        }
        return false;
    }

    private void serveFile(FileReference reference, Receiver target) {
        File file = root.getFile(reference);
        // TODO remove once verified in system tests.
        log.info("Start serving reference '" + reference.value() + "' with file '" + file.getAbsolutePath() + "'");
        byte [] blob = new byte [0];
        boolean success = false;
        String errorDescription = "OK";
        try {
            blob = IOUtils.readFileBytes(file);
            success = true;
        } catch (IOException e) {
            errorDescription = "For file reference '" + reference.value() + "' I failed reading file '" + file.getAbsolutePath() + "'";
            log.warning(errorDescription + "for sending to '" + target.toString() + "'. " + e.toString());
        }
        target.receive(reference, file.getName(), blob,
                new ReplayStatus(success ? 0 : 1, success ? "OK" : errorDescription));
        // TODO remove once verified in system tests.
        log.info("Done serving reference '" + reference.toString() + "' with file '" + file.getAbsolutePath() + "'");
    }
}
