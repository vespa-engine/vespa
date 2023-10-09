// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.model;

import com.yahoo.component.provider.FreezableClass;

/**
 * Abstract superclass of various kinds of choices.
 *
 * @author bratseth
 */
public abstract class AbstractChoice extends FreezableClass implements PageElement {

    private String method;

    /**
     * Returns the choice method to use - a string interpreted by the resolver in use,
     * or null to use any available method
     */
    public String getMethod() { return method; }

    public void setMethod(String method) {
        ensureNotFrozen();
        this.method=method;
    }

    /** Returns true if this choice is (partially or completely) a choice between the given type */
    @SuppressWarnings("rawtypes")
    public abstract boolean isChoiceBetween(Class pageTemplateModelClass);

}
