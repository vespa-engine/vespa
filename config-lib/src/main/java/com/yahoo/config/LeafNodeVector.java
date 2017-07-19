// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A vector of leaf nodes.
 *
 * @author gjoranv
 * @since 5.1.4
 */
public class LeafNodeVector<REAL, NODE extends LeafNode<REAL>> extends NodeVector<NODE> {

    private final List<REAL> realValues;

    // TODO: take class instead of default node
    public LeafNodeVector(List<REAL> values, NODE defaultNode) {
        assert (defaultNode != null) : "The default node cannot be null";

        if (createNew(defaultNode) == null) {
            throw new NullPointerException("Unable to duplicate the default node.");
        }

        for (REAL value : values) {
            NODE node = createNew(defaultNode);
            node.value = value;
            vector.add(node);
        }
        realValues = realList(vector);
    }

    /**
     * Creates a new Node by cloning the default node.
     */
    @SuppressWarnings("unchecked")
    private NODE createNew(NODE defaultNode) {
        return (NODE) (defaultNode).clone();
    }

    private List<REAL> realList(List<NODE> nodes) {
        List<REAL> reals = new ArrayList<REAL>();
        for(NODE node : vector) {
            reals.add(node.value());
        }
        return Collections.unmodifiableList(reals);
    }

    @SuppressWarnings("unchecked")
    public List<REAL> asList() {
        return realValues;
    }

    // TODO: Try to eliminate the need for this method when we have moved FileAcquirer to the config library
    // It is needed now because the builder has a list of String, while REAL=FileReference.
    public static LeafNodeVector<FileReference, FileNode> createFileNodeVector(Collection<String> values) {
        List<FileReference> fileReferences = new ArrayList<FileReference>();
         for (String s : values)
             fileReferences.add(new FileReference(ReferenceNode.stripQuotes(s)));

        return new LeafNodeVector<FileReference, FileNode>(fileReferences, new FileNode());
    }

    public static LeafNodeVector<Path, PathNode> createPathNodeVector(Collection<FileReference> values) {
        List<Path> paths = new ArrayList<>();
        for (FileReference fileReference : values)
            paths.add(Paths.get(fileReference.value()));

        return new LeafNodeVector<Path, PathNode>(paths, new PathNode());
    }
}
