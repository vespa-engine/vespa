// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates;

import com.yahoo.search.pagetemplates.model.PageTemplateVisitor;
import com.yahoo.search.pagetemplates.model.Source;

import java.util.HashSet;
import java.util.Set;

/**
 * Visits a page template object structure and records the sources mentioned.
 *
 * @author bratseth
 */
class SourceVisitor extends PageTemplateVisitor {

    private Set<Source> sources=new HashSet<>();

    @Override
    public void visit(Source source) {
        sources.add(source);
    }

    /** Returns the live list of sources collected by this during visiting */
    public Set<Source> getSources() { return sources; }

}
