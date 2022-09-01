// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.io.File;
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

    public List<REAL> asList() {
        return realValues;
    }

    /**
     * Creates a new Node by cloning the default node.
     */
    @SuppressWarnings("unchecked")
    private static <NODE extends LeafNode<?>> NODE createNew(NODE defaultNode) {
        return (NODE) (defaultNode).clone();
    }

    private static<REAL, NODE extends LeafNode<REAL>> List<REAL> realList(List<NODE> nodes) {
        List<REAL> reals = new ArrayList<>();
        for(NODE node : nodes) {
            reals.add(node.value());
        }
        return Collections.unmodifiableList(reals);
    }

    // TODO: Try to eliminate the need for this method when we have moved FileAcquirer to the config library
    // It is needed now because the builder has a list of String, while REAL=FileReference.
    public static LeafNodeVector<FileReference, FileNode> createFileNodeVector(Collection<String> values) {
        List<FileReference> fileReferences = new ArrayList<FileReference>();
         for (String s : values)
             fileReferences.add(new FileReference(ReferenceNode.stripQuotes(s)));

        return new LeafNodeVector<>(fileReferences, new FileNode());
    }

    public static LeafNodeVector<Path, PathNode> createPathNodeVector(Collection<FileReference> values) {
        List<Path> paths = new ArrayList<>();
        for (FileReference fileReference : values)
            paths.add(Paths.get(fileReference.value()));
        return new LeafNodeVector<>(paths, new PathNode());
    }

    public static LeafNodeVector<File, UrlNode> createUrlNodeVector(Collection<UrlReference> values) {
        List<File> files = new ArrayList<>();
        for (UrlReference urlReference : values)
            files.add(new File(urlReference.value()));
        return new LeafNodeVector<>(files, new UrlNode());
    }

    public static LeafNodeVector<Path, ModelNode> createModelNodeVector(Collection<ModelReference> values) {
        List<Path> modelPaths = new ArrayList<>();
        for (ModelReference modelReference : values)
            modelPaths.add(modelReference.value());
        return new LeafNodeVector<>(modelPaths, new ModelNode());
    }

}
