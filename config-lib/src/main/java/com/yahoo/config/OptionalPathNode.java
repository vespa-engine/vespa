// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a 'path' in a {@link ConfigInstance}, usually a filename, can be optional
 *
 * @author hmusum
 */
public class OptionalPathNode extends LeafNode<Optional<Path>> {

    private final Optional<FileReference> fileReference;

    public OptionalPathNode() {
        fileReference = Optional.empty();
    }

    public OptionalPathNode(FileReference fileReference) {
        super(true);
        this.value = Optional.of(Path.of(fileReference.value()));
        this.fileReference = Optional.of(fileReference);
    }

    public OptionalPathNode(Optional<FileReference> fileReference) {
        super(true);
        this.value = fileReference.map(reference -> Path.of(reference.value()));
        this.fileReference = fileReference;
    }

    public Optional<Path> value() {
        return value;
    }

    @Override
    public String getValue() {
        return value.toString();
    }

    @Override
    public String toString() {
        return (value.isEmpty()) ? "(empty)" : '"' + value.get().toString() + '"';
    }

    @Override
    protected boolean doSetValue(String stringVal) {
        throw new UnsupportedOperationException("doSetValue should not be necessary anymore!");
    }

    @Override
    void serialize(String name, Serializer serializer) {
        value.ifPresent(path -> serializer.serialize(name, path.toString()));
    }

    @Override
    void serialize(Serializer serializer) {
        value.ifPresent(path -> serializer.serialize(path.toString()));
    }

    public Optional<FileReference> getFileReference() {
        return fileReference;
    }

    public static List<Optional<FileReference>> toFileReferences(List<OptionalPathNode> pathNodes) {
        List<Optional<FileReference>> fileReferences = new ArrayList<>();
        for (OptionalPathNode pathNode : pathNodes)
            fileReferences.add(pathNode.getFileReference());
        return fileReferences;
    }

    public static Map<String, Optional<FileReference>> toFileReferenceMap(Map<String, OptionalPathNode> map) {
        Map<String, Optional<FileReference>> ret = new LinkedHashMap<>();
        for (Map.Entry<String, OptionalPathNode> e : map.entrySet()) {
            ret.put(e.getKey(), e.getValue().getFileReference());
        }
        return ret;
    }

}
