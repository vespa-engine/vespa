// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.path.Path;

import java.nio.ByteBuffer;
import java.util.Objects;

public class DistributableResource implements Comparable <DistributableResource> {

    public enum PathType { FILE, URI, BLOB }

    /** The search definition-unique name of this constant */
    private final String name;
    // TODO: Make path/pathType final
    private PathType pathType;
    private String path;
    private FileReference fileReference = new FileReference("");

    public PathType getPathType() {
        return pathType;
    }

    public DistributableResource(String name) {
        this(name, null, PathType.FILE);
    }
    public DistributableResource(String name, String path) {
        this(name, path, PathType.FILE);
    }
    public DistributableResource(String name, String path, PathType type) {
        this.name = name;
        this.path = path;
        this.pathType = type;
    }

    // TODO: Remove and make path/pathType final
    public void setFileName(String fileName) {
        Objects.requireNonNull(fileName, "Filename cannot be null");
        this.path = fileName;
        this.pathType = PathType.FILE;
    }

    // TODO: Remove and make path/pathType final
    public void setUri(String uri) {
        Objects.requireNonNull(uri, "uri cannot be null");
        this.path = uri;
        this.pathType = PathType.URI;
    }

    public String getName() { return name; }
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
        }
    }

    public void register(FileRegistry fileRegistry) {
        switch (pathType) {
            case FILE -> fileReference = fileRegistry.addFile(path);
            case URI -> fileReference = fileRegistry.addUri(path);
            default -> throw new IllegalArgumentException("Unknown path type " + pathType);
        }
    }

    protected void register(FileRegistry fileRegistry, ByteBuffer blob) {
        fileReference = fileRegistry.addBlob(path, blob);
    }

    @Override
    public String toString() {
        return "resource '" + name + " of type '" + pathType + "' with ref '" + fileReference + "'";
    }

    @Override
    public int compareTo(DistributableResource o) {
        return name.compareTo(o.getName());
    }

}
