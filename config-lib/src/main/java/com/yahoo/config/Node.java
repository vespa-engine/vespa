// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

/**
 * The Node class is superclass for all nodes in a {@link
 * ConfigInstance}.  Important subclasses of this node are {@link
 * InnerNode} and {@link LeafNode}.
 */
public abstract class Node {

    /**
     * Post-initialize this node. Any node needing to process its values depending on the config
     * id should override this method.
     *
     * @param configId the configId of the ConfigInstance that owns (or is) this node
     */
    public void postInitialize(String configId) {}

    /**
     * This method is meant for internal use in the configuration system.
     * Overrides Object.clone(), and is overridden by LeafNode.clone().
     *
     * @return a new instance similar to this object.
     */
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

}
