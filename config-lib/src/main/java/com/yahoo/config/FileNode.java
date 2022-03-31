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
        this.value = new FileReference(ReferenceNode.stripQuotes(stringVal));
        if (Path.of(value.value()).normalize().startsWith(".."))
            throw new IllegalArgumentException("path may not start with '..', but got: " + value.value());
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
