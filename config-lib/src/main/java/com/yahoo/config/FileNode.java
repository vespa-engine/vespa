// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.nio.file.Path;

/**
 * Represents a 'file' in a {@link ConfigInstance}, usually a filename.
 *
 * @author gjoranv
 */
public class FileNode extends LeafNode<FileReference> {

    public FileNode() {
    }

    public FileNode(String stringVal) {
        super(true);
        Path path = Path.of(ReferenceNode.stripQuotes(stringVal)).normalize();
        if (path.isAbsolute())
            throw new IllegalArgumentException("path must be relative");
        if (path.startsWith(".."))
            throw new IllegalArgumentException("'..' not allowed in path");

        this.value = new FileReference(path.toString());
    }

    public FileReference value() {
        return value;
    }

    @Override
    public String getValue() {
        return value.value();
    }

    @Override
    public String toString() {
        return (value == null) ? "(null)" : '"' + getValue() + '"';
    }

    @Override
    protected boolean doSetValue(String stringVal) {
        value = new FileReference(ReferenceNode.stripQuotes(stringVal));
        return true;
    }


}
