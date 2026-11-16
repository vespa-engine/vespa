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

    private String name;

    /**
     * Constructs a new instance of this class.
     *
     * @param attributeName the attribute name to assign to this.
     */
    public AttributeFunction(String attributeName) {
        this("attribute(" + attributeName + ")", null, null, attributeName);
    }

    private AttributeFunction(String image, String label, Integer level, String attributeName) {
        super(image, label, level);
        this.name = attributeName;
    }

    @Override
    public AttributeFunction copy() {
        return new AttributeFunction(getImage(), getLabel(), getLevelOrNull(), getAttributeName());
    }

    /**
     * Returns the name of the attribute to retrieve from the input hit.
     *
     * @return the attribute name.
     */
    public String getAttributeName() {
        return name;
    }

    /**
     * Sets the name of the attribute to retrieve, e.g. to replace a field alias by the name it refers to.
     * This does not change the image of this expression, so result labels are kept as written.
     */
    public void setAttributeName(String attributeName) {
        this.name = attributeName;
    }

}
