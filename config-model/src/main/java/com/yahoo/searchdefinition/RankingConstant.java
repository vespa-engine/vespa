// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.FileReference;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.utils.FileSender;

import java.util.Collection;
import java.util.Objects;

/**
 * A global ranking constant distributed using file distribution.
 * Ranking constants must be sent to some services to be useful - this is done
 * by calling the sentTo method during the prepare phase of building models.
 *
 * @author arnej
 * @author bratseth
 */
public class RankingConstant {

    public enum PathType {FILE, URI};

    /** The search definition-unique name of this constant */
    private final String name;
    private TensorType tensorType = null;
    private String path = null;
    private String fileReference = "";

    public PathType getPathType() {
        return pathType;
    }

    private PathType pathType = PathType.FILE;

    public RankingConstant(String name) {
        this.name = name;
    }

    public RankingConstant(String name, TensorType type, String fileName) {
        this(name);
        this.tensorType = type;
        this.path = fileName;
        validate();
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

    public void setType(TensorType tensorType) { this.tensorType = tensorType; }

    /** Initiate sending of theis constant to some services over file distribution */
    public void sendTo(Collection<? extends AbstractService> services) {
        FileReference reference = (pathType == RankingConstant.PathType.FILE)
                                  ? FileSender.sendFileToServices(path, services)
                                  : FileSender.sendUriToServices(path, services);
        this.fileReference = reference.value();
    }

    public String getName() { return name; }
    public String getFileName() { return path; }
    public String getUri() { return path; }
    public String getFileReference() { return fileReference; }
    public TensorType getTensorType() { return tensorType; }
    public String getType() { return tensorType.toString(); }

    public void validate() {
        if (path == null || path.isEmpty())
            throw new IllegalArgumentException("Ranking constants must have a file or uri.");
        if (tensorType == null)
            throw new IllegalArgumentException("Ranking constant '" + name + "' must have a type.");
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("constant '").append(name)
         .append(pathType == PathType.FILE ? "' from file '" : " from uri ").append(path)
         .append("' with ref '").append(fileReference)
         .append("' of type '").append(tensorType)
         .append("'");
        return b.toString();
    }

}
