// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a document attribute function in a {@link GroupingExpression}. It evaluates to the value of the
 * named attribute in the input {@link com.yahoo.search.result.Hit}.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class AttributeFunction extends DocumentValue {

    private final String name;

    /**
     * Constructs a new instance of this class.
     *
     * @param attributeName the attribute name to assign to this.
     */
    public AttributeFunction(String attributeName) {
        this(null, null, attributeName);
    }

    private AttributeFunction(String label, Integer level, String attributeName) {
        super("attribute(" + attributeName + ")", label, level);
        this.name = attributeName;
    }

    @Override
    public AttributeFunction copy() {
        return new AttributeFunction(getLabel(), getLevelOrNull(), getAttributeName());
    }

    /**
     * Returns the name of the attribute to retrieve from the input hit.
     *
     * @return the attribute name.
     */
    public String getAttributeName() {
        return name;
    }

}
