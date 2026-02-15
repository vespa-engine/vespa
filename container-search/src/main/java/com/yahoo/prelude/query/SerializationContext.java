// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.api.annotations.Beta;

/**
 * Context needed to serialize a query tree.
 *
 * @author bratseth
 */
@Beta
public class SerializationContext {

    private final double contentShare;

    /**
     * Creates a serialization context.
     *
     * @param contentShare the share of the total content to be queried (across all nodes in the queried group)
     *        which is being queried on the node we are serializing for here
     */
    public SerializationContext(double contentShare) {
        this.contentShare = contentShare;
    }

    public int contentShareOf(int value) {
        return (int)Math.ceil(value * contentShare);
    }

    /** Creates an instance of this which is not expected to be used. */
    public static SerializationContext ignored() {
        return new SerializationContext(1.0);
    }

}