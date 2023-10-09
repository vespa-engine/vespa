// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.ml;

import com.yahoo.path.Path;

/**
 * Models used in a rank profile has the rank profile name as name space while global model names have no namespace
 *
 * @author bratseth
 */
public class ModelName {

    /** The namespace, or null if none */
    private final String namespace;
    private final String name;
    private final String fullName;

    public ModelName(String name) {
        this(null, name);
    }

    public ModelName(String namespace, Path modelPath, boolean pathIsFile) {
        this(namespace,
             stripFileEndingIfFile(modelPath, pathIsFile).toString().replace("/", "_"));
    }

    private ModelName(String namespace, String name) {
        this.namespace = namespace;
        this.name = name;
        this.fullName = (namespace != null ? namespace + "." : "") + name;
    }

    private static Path stripFileEndingIfFile(Path path, boolean pathIsFile) {
        if ( ! pathIsFile) return path;
        int dotIndex = path.last().lastIndexOf(".");
        if (dotIndex <= 0) return path;
        return path.withLast(path.last().substring(0, dotIndex));
    }

    /** Returns true if the local name of this is not in a namespace */
    public boolean isGlobal() { return namespace == null; }

    /** Returns the namespace, or null if this is global */
    public String namespace() { return namespace; }
    public String localName() { return name; }
    public String fullName() { return fullName; }


    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof ModelName)) return false;
        return ((ModelName)o).fullName.equals(this.fullName);
    }

    @Override
    public int hashCode() { return fullName.hashCode(); }

    @Override
    public String toString() { return fullName; }

}
