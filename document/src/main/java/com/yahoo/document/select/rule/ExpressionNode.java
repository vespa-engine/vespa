// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.rule;

import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.select.BucketSet;
import com.yahoo.document.select.Context;
import com.yahoo.document.select.OrderingSpecification;
import com.yahoo.document.select.Visitor;

/**
 * This is the interface of all expression nodes. It declares the methods requires by all expression nodes to maintain
 * a working document selector language.
 *
 * @author Simon Thoresen Hult
 */
public interface ExpressionNode {

    /**
     * Evaluate the content of this node based on document object, and return that value.
     *
     * @param doc The document to evaluate over.
     * @return The value of this.
     */
    public Object evaluate(Context doc);

    /**
     * Returns the set of bucket ids covered by this node.
     *
     * @param factory The factory used by the current application.
     */
    public BucketSet getBucketSet(BucketIdFactory factory);

    /**
     * If this document selection implies a specific ordering (using the orderdoc scheme),
     * return that specification.  
     *
     * @param order The order in which we are looking to traverse the ordering (ASCENDING or DESCENDING)
     */
    public OrderingSpecification getOrdering(int order);

    /**
     * Perform visitation of this node.
     *
     * @param visitor The visitor that wishes to visit the node.
     */
    public void accept(Visitor visitor);

}
