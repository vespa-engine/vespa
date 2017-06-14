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

    NODE defaultNode;

    /**
     * Creates a new vector with the given default node.
     */
    // TODO: remove this ctor when the library uses reflection via builders, and resizing won't be necessary
    public LeafNodeVector(NODE defaultNode) {
        assert (defaultNode != null) : "The default node cannot be null";

        this.defaultNode = defaultNode;
        if (createNew() == null) {
            throw new NullPointerException("Unable to duplicate the default node.");
        }
    }

    // TODO: take class instead of default node when the library uses reflection via builders
    public LeafNodeVector(List<REAL> values, NODE defaultNode) {
        this(defaultNode);
        for (REAL value : values) {
            NODE node = createNew();
            node.value = value;
            vector.add(node);
        }
    }

    /**
     * Creates a new Node by cloning the default node.
     */
    @SuppressWarnings("unchecked")
    protected NODE createNew() {
        return (NODE) (defaultNode).clone();
    }

    // TODO: create unmodifiable list in ctor when the library uses reflection via builders
    @SuppressWarnings("unchecked")
    public List<REAL> asList() {
        List<REAL> ret = new ArrayList<REAL>();
        for(NODE node : vector) {
            ret.add(node.value());
        }
        return Collections.unmodifiableList(ret);
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
