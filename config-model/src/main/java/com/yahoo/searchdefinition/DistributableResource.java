package com.yahoo.searchdefinition;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.path.Path;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.utils.FileSender;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Objects;

public class DistributableResource {
    public enum PathType { FILE, URI, BLOB };

    /** The search definition-unique name of this constant */
    private final String name;
    private final ByteBuffer blob;
    private String path;
    private FileReference fileReference = new FileReference("");
    private PathType pathType = PathType.FILE;

    public PathType getPathType() {
        return pathType;
    }

    public DistributableResource(String name) {
        this.name = name;
        blob = null;
    }
    public DistributableResource(String name, String path) {
        this.name = name;
        this.path = path;
        blob = null;
    }
    public DistributableResource(String name, ByteBuffer blob) {
        Objects.requireNonNull(name, "Blob name cannot be null");
        Objects.requireNonNull(blob, "Blob cannot be null");
        this.name = name;
        this.blob = blob;
        path = name + ".lz4";
        pathType = PathType.BLOB;
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

    /** Initiate sending of this constant to some services over file distribution */
    public void sendTo(Collection<? extends AbstractService> services) {
        FileSender.send(fileReference, services);
    }

    public String getName() { return name; }
    public ByteBuffer getBlob() { return blob; }
    public String getFileName() { return path; }
    public Path getFilePath() { return Path.fromString(path); }
    public String getUri() { return path; }
    public String getFileReference() { return fileReference.value(); }

    public void validate() {
        switch (pathType) {
            case FILE:
            case URI:
                if (path == null || path.isEmpty())
                    throw new IllegalArgumentException("Distributable URI/FILE resource must have a file or uri.");
                break;
            case BLOB:
                if (blob == null)
                    throw new IllegalArgumentException("Distributable BLOB can not be null.");
        }
    }

    void register(FileRegistry fileRegistry) {
        switch (pathType) {
            case FILE:
                fileReference = fileRegistry.addFile(path);
                break;
            case URI:
                fileReference = fileRegistry.addUri(path);
                break;
            case BLOB:
                fileReference = fileRegistry.addBlob(path, blob);
                break;
            default:
                throw new IllegalArgumentException("Unknown path type " + pathType);
        }
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("resource '").append(name).append(" of type '").append(pathType)
                .append("' with ref '").append(fileReference).append("'");
        return b.toString();
    }
}
