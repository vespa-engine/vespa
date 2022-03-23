// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a document id specific value in a {@link GroupingExpression}. It evaluates to the namespace-
 * specific value of the document id of the input {@link com.yahoo.search.result.Hit}.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class DocIdNsSpecificValue extends DocumentValue {

    /** Constructs a new instance of this class. */
    public DocIdNsSpecificValue() {
        this(null, null);
    }

    private DocIdNsSpecificValue(String label, Integer level) {
        super("docidnsspecific()", label, level);
    }

    @Override
    public DocIdNsSpecificValue copy() {
        return new DocIdNsSpecificValue(getLabel(), getLevelOrNull());
    }

}

