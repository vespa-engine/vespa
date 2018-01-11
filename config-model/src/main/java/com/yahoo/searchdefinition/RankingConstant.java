// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.tensor.TensorType;

import java.util.Objects;

/**
 * Represents a global ranking constant
 *
 * @author arnej
 */
public class RankingConstant {

    /** The search definition-unique name of this constant */
    private final String name;
    private TensorType tensorType = null;
    private String fileName = null;
    private String fileReference = "";

    public RankingConstant(String name) {
        this.name = name;
    }

    public RankingConstant(String name, TensorType type, String fileName) {
        this(name);
        this.tensorType = type;
        this.fileName = fileName;
        validate();
    }

    public void setFileName(String fileName) {
        Objects.requireNonNull(fileName, "Filename cannot be null");
        this.fileName = fileName;
    }

    /**
     * Set the internally generated reference to this file used to identify this instance of the file for
     * file distribution.
     */
    public void setFileReference(String fileReference) { this.fileReference = fileReference; }

    public void setType(TensorType tensorType) { this.tensorType = tensorType; }

    public String getName() { return name; }
    public String getFileName() { return fileName; }
    public String getFileReference() { return fileReference; }
    public TensorType getTensorType() { return tensorType; }
    public String getType() { return tensorType.toString(); }

    public void validate() {
        if (fileName == null || fileName.isEmpty())
            throw new IllegalArgumentException("Ranking constants must have a file.");
        if (tensorType == null)
            throw new IllegalArgumentException("Ranking constant '" + name + "' must have a type.");
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("constant '").append(name)
         .append("' from file '").append(fileName)
         .append("' with ref '").append(fileReference)
         .append("' of type '").append(tensorType)
         .append("'");
        return b.toString();
    }

}
