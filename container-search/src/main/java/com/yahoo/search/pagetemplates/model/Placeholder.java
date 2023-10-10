// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.model;

/**
 * A source placeholder is replaced with a list of source instances at evaluation time.
 * Source placeholders may not have any content themselves - attempting to call any setter on this
 * results in a IllegalStateException.
 *
 * @author bratseth
 */
public class Placeholder implements PageElement {

    private String id;

    private MapChoice valueContainer=null;

    /** Creates a source placeholder with an id. */
    public Placeholder(String id) {
        this.id=id;
    }

    public String getId() { return id; }

    /** Returns the element which contains the value(s) of this placeholder. Never null. */
    public MapChoice getValueContainer() { return valueContainer; }

    public void setValueContainer(MapChoice valueContainer) { this.valueContainer=valueContainer; }

    @Override
    public void freeze() {}

    /** Accepts a visitor to this structure */
    @Override
    public void accept(PageTemplateVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return "source placeholder '" + id + "'";
    }

    /**
     * This method always returns false, is a Placeholder always is mutable.
     * (freeze() is a NOOP.)
     */
    @Override
    public boolean isFrozen() {
        return false;
    }

}
