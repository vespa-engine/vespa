// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.rule;

import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.select.BucketSet;
import com.yahoo.document.select.Context;
import com.yahoo.document.select.Visitor;

/**
 * The interface of all expression nodes. It declares the methods requires by all expression nodes to maintain
 * a working document selector language.
 *
 * @author Simon Thoresen Hult
 */
public interface ExpressionNode {

    /**
     * Evaluate the content of this node based on document object, and return that value.
     *
     * @param doc the document to evaluate over
     * @return the value of this
     */
    Object evaluate(Context doc);

    /**
     * Returns the set of bucket ids covered by this node.
     *
     * @param factory the factory used by the current application
     */
    BucketSet getBucketSet(BucketIdFactory factory);

    /**
     * Perform visitation of this node.
     *
     * @param visitor the visitor that wishes to visit the node
     */
    void accept(Visitor visitor);

}
