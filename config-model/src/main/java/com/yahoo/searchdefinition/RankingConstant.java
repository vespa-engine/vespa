// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.tensor.TensorType;

import java.util.Objects;

/**
 * Represents a global ranking constant (declared in a .sd file)
 *
 * @author arnej
 */
public class RankingConstant {

    /** The search definition-unique name of this constant */
    private final String name;
    private TensorType tensorType = null;
    private String fileName = null;
    private String fileRef = "";

    public RankingConstant(String name) {
        this.name = name;
    }

    public void setFileName(String fileName) { 
        Objects.requireNonNull(fileName, "Filename cannot be null");
        this.fileName = fileName; 
    }

    public void setFileReference(String fileRef) { this.fileRef = fileRef; }
    public void setType(TensorType tensorType) { this.tensorType = tensorType; }

    public String getName() { return name; }
    public String getFileName() { return fileName; }
    public String getFileReference() { return fileRef; }
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
         .append("' with ref '").append(fileRef)
         .append("' of type '").append(tensorType)
         .append("'");
        return b.toString();
    }

}
