// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.util.List;

/**
 * @author gjoranv
 * @since 5.1.4
 */
public class InnerNodeVector<NODE extends InnerNode> extends NodeVector<NODE> {

    NODE defaultNode;

    /**
     * Creates a new vector with the given default node.
     */
    // TODO: remove this ctor when the library uses reflection via builders, and resizing won't be necessary
    public InnerNodeVector(NODE defaultNode) {
        assert (defaultNode != null) : "The default node cannot be null";

        this.defaultNode = defaultNode;
        if (createNew() == null) {
            throw new NullPointerException("Unable to duplicate the default node.");
        }
    }

    public InnerNodeVector(List<NODE> nodes, NODE defaultNode) {
        this(defaultNode);
        for (NODE node : nodes) {
            vector.add(node);
        }
    }

    /**
     * Creates a new Node by creating a new instance with the 0-argument constructor
     */
    // TODO: remove when the library uses reflection via builders
    @SuppressWarnings("unchecked")
    protected NODE createNew() {
        try {
            return (NODE) defaultNode.getClass().newInstance();
        } catch (IllegalAccessException | InstantiationException ex) {
            throw new ConfigurationRuntimeException(ex);
        }
    }

}
