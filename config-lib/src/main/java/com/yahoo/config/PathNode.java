// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a 'path' in a {@link ConfigInstance}, usually a filename.
 *
 * @author gjoranv
 */
public class PathNode extends LeafNode<Path> {

    private final FileReference fileReference;

    public PathNode() {
        fileReference = null;
    }

    public PathNode(FileReference fileReference) {
        super(true);
        this.value = Path.of(fileReference.value());
        this.fileReference = fileReference;
    }

    public Path value() {
        return value;
    }

    @Override
    public String getValue() {
        return value.toString();
    }

    @Override
    public String toString() {
        return (value == null) ? "(null)" : '"' + getValue() + '"';
    }

    @Override
    protected boolean doSetValue(String stringVal) {
        throw new UnsupportedOperationException("doSetValue should not be necessary since the library anymore!");
    }

    public FileReference getFileReference() {
        return fileReference;
    }

    public static List<FileReference> toFileReferences(List<PathNode> pathNodes) {
        List<FileReference> fileReferences = new ArrayList<>();
        for (PathNode pathNode : pathNodes)
            fileReferences.add(pathNode.getFileReference());
        return fileReferences;
    }

    public static Map<String, FileReference> toFileReferenceMap(Map<String, PathNode> map) {
        Map<String, FileReference> ret = new LinkedHashMap<>();
        for (Map.Entry<String, PathNode> e : map.entrySet()) {
            ret.put(e.getKey(), e.getValue().getFileReference());
        }
        return ret;
    }

}
