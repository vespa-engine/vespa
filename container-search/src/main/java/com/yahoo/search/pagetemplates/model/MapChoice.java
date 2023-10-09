// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A choice between different possible mapping functions of a set of values to a set of placeholder ids.
 * A <i>resolution</i> of this choice consists of choosing a unique value for each placeholder id
 * (hence a map choice is valid iff there are at least as many values as placeholder ids).
 * <p>
 * Each unique set of mappings (pairs) from values to placeholder ids is a separate possible
 * alternative of this choice. The alternatives are not listed explicitly but are generated as needed.
 *
 * @author bratseth
 */
public class MapChoice extends AbstractChoice {

    private List<String> placeholderIds=new ArrayList<>();

    private List<List<PageElement>> values=new ArrayList<>();

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public boolean isChoiceBetween(Class pageTemplateModelElementClass) {
        List<PageElement> firstNonEmpty=null;
        for (List<PageElement> value : values)
            if (value.size()>0)
                firstNonEmpty=value;
        if (firstNonEmpty==null) return false;
        return (pageTemplateModelElementClass.isAssignableFrom(firstNonEmpty.get(0).getClass()));
    }

    /**
     * Returns the placeholder ids (the "to" of the mapping) of this as a live reference which can be modified unless
     * this is frozen.
     */
    public List<String> placeholderIds() { return placeholderIds; }

    /**
     * Returns the values (the "from" of the mapping) of this as a live reference which can be modified unless
     * this is frozen. Note that each single choice of values within this is also a list of values. This is
     * the inner list.
     */
    public List<List<PageElement>> values() { return values; }

    @Override
    public void freeze() {
        if (isFrozen()) return;
        super.freeze();
        placeholderIds=Collections.unmodifiableList(placeholderIds);
        values=Collections.unmodifiableList(values);
    }

    /** Accepts a visitor to this structure */
    @Override
    public void accept(PageTemplateVisitor visitor) {
        visitor.visit(this);
        for (List<PageElement> valueEntry : values)
            for (PageElement value : valueEntry)
                value.accept(visitor);
    }

    @Override
    public String toString() {
        return "mapping to placeholders " + placeholderIds;
    }

}
