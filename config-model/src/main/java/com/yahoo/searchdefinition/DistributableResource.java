package com.yahoo.searchdefinition;

import com.yahoo.config.FileReference;
import com.yahoo.path.Path;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.utils.FileSender;

import java.util.Collection;
import java.util.Objects;

public class DistributableResource {
    public enum PathType { FILE, URI };

    /** The search definition-unique name of this constant */
    private final String name;
    private String path = null;
    private String fileReference = "";
    private PathType pathType = PathType.FILE;

    public PathType getPathType() {
        return pathType;
    }

    public DistributableResource(String name) {
        this(name, null);
    }
    public DistributableResource(String name, String path) {
        this.name = name;
        this.path = path;
    }

    public void setFileName(String fileName) {
        Objects.requireNonNull(fileName, "Filename cannot be null");
        this.path = fileName;
        this.pathType = PathType.FILE;
    }

    public void setUri(String uri) {
        Objects.requireNonNull(uri, "uri cannot be null");
        this.path = uri;
        this.pathType = PathType.URI;
    }

    protected void setFileReference(String fileReference) { this.fileReference = fileReference; }
    /** Initiate sending of this constant to some services over file distribution */
    public void sendTo(Collection<? extends AbstractService> services) {
        FileReference reference = (pathType == PathType.FILE)
                ? FileSender.sendFileToServices(path, services)
                : FileSender.sendUriToServices(path, services);
        this.fileReference = reference.value();
    }

    public String getName() { return name; }
    public String getFileName() { return path; }
    public Path getFilePath() { return Path.fromString(path); }
    public String getUri() { return path; }
    public String getFileReference() { return fileReference; }

    public void validate() {
        if (path == null || path.isEmpty())
            throw new IllegalArgumentException("Distributable resource must have a file or uri.");
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("resource '").append(name)
                .append(pathType == PathType.FILE ? "' from file '" : " from uri ").append(path)
                .append("' with ref '").append(fileReference)
                .append("'");
        return b.toString();
    }
}
