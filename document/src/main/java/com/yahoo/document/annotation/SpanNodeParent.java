// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.datatypes.StringFieldValue;

/**
 * An interface to be implemented by classes that can be parents of SpanNodes.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @see SpanNode#getParent()
 */
public interface SpanNodeParent {
    /**
     * Returns the SpanTree of this, if any.
     *
     * @return the SpanTree of this, if it belongs to a SpanTree, otherwise null.
     */
    public SpanTree getSpanTree();

    /**
     * Returns the StringFieldValue that this node belongs to, if any.
     *
     * @return the StringFieldValue that this node belongs to, if any, otherwise null.
     */
    public StringFieldValue getStringFieldValue();
}
