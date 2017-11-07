package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;

public class FileServer {
    private final String rootDir;
    public FileServer(String rootDir) {
        this.rootDir = rootDir;
    }
    public boolean hasFile(String fileName) {
        return hasFile(new FileReference(fileName));
    }
    public boolean hasFile(FileReference ref) {
        return false;
    }
}
