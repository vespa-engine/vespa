package com.yahoo.vespa.config.server.filedistribution;

import com.google.inject.Inject;
import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.io.IOUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class FileServer {
    private static final Logger log = Logger.getLogger(FileServer.class.getName());
    private final String rootDir;
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

    private String getPath(FileReference ref) {
        return rootDir + "/" + ref.value();
    }

    static private class Filter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            return !".".equals(name) && !"..".equals(name) ;
        }
    }
    private File getFile(FileReference reference) {
        File dir = new File(getPath(reference));
        if (!dir.exists()) {
            throw new IllegalArgumentException("File reference '" + reference.toString() + "' with absolute path '" + dir.getAbsolutePath() + "' does not exist.");
        }
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("File reference '" + reference.toString() + "' with absolute path '" + dir.getAbsolutePath() + "' is not a directory.");
        }
        File [] files = dir.listFiles(new Filter());
        if (files.length != 1) {
            StringBuilder msg = new StringBuilder();
            for (File f: files) {
                msg.append(f.getName()).append("\n");
            }
            throw new IllegalArgumentException("File reference '" + reference.toString() + "' with absolute path '" + dir.getAbsolutePath() + " does not contain exactly one file, but [" + msg.toString() + "]");
        }
        return files[0];
    }

    @Inject
    public FileServer() {
        this(FileDistribution.getDefaultFileDBRoot());
    }

    public FileServer(String rootDir) {
        this(rootDir, Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
    }

    public FileServer(String rootDir, ExecutorService executor) {
        this.rootDir = rootDir;
        this.executor = executor;
    }
    public boolean hasFile(String fileName) {
        return hasFile(new FileReference(fileName));
    }
    public boolean hasFile(FileReference reference) {
        try {
            return getFile(reference).exists();
        } catch (IllegalArgumentException e) {
            log.warning("Failed locating file reference '" + reference + "' with error " + e.toString());
        }
        return false;
    }
    public boolean startFileServing(String fileName, Receiver target) {
        FileReference reference = new FileReference(fileName);
        File file = getFile(reference);

        if (file.exists()) {
            executor.execute(() -> serveFile(reference, target));
        }
        return false;
    }

    private void serveFile(FileReference reference, Receiver target) {

        File file = getFile(reference);
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
    }
}
