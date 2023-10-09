// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates;

import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.FreezableComponent;
import com.yahoo.search.pagetemplates.model.PageElement;
import com.yahoo.search.pagetemplates.model.PageTemplateVisitor;
import com.yahoo.search.pagetemplates.model.Section;
import com.yahoo.search.pagetemplates.model.Source;

import java.util.Collections;
import java.util.Set;

/**
 * A page template represents a particular way to organize a result page. It is a recursive structure of
 * page template elements.
 *
 * @author bratseth
 */
public final class PageTemplate extends FreezableComponent implements PageElement {

    /** The root section of this page */
    private Section section=new Section();

    /** The sources mentioned (recursively) in this page template, or null if this is not frozen */
    private Set<Source> sources=null;

    public PageTemplate(ComponentId id) {
        super(id);
    }

    public void setSection(Section section) {
        ensureNotFrozen();
        this.section=section;
    }

    /** Returns the root section of this. This is never null. */
    public Section getSection() { return section; }

    /**
     * Returns an unmodifiable set of all the sources this template <i>may</i> include (depending on choice resolution).
     * If the template allows (somewhere) the "any" source (*), Source.any will be in the set returned.
     * This operation is fast on frozen page templates (i.e at execution time).
     */
    public Set<Source> getSources() {
        if (isFrozen()) return sources;
        SourceVisitor sourceVisitor=new SourceVisitor();
        getSection().accept(sourceVisitor);
        return Collections.unmodifiableSet(sourceVisitor.getSources());
    }

    @Override
    public void freeze() {
        if (isFrozen()) return;
        resolvePlaceholders();
        section.freeze();
        sources=getSources();
        super.freeze();
    }

    /** Validates and creates the necessary internal references between placeholders and their resolving choices */
    private void resolvePlaceholders() {
        try {
            PlaceholderMappingVisitor placeholderMappingVisitor=new PlaceholderMappingVisitor();
            accept(placeholderMappingVisitor);
            accept(new PlaceholderReferenceCreatingVisitor(placeholderMappingVisitor.getMap()));
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(this + " is invalid",e);
        }
    }

    /** Accepts a visitor to this structure */
    @Override
    public void accept(PageTemplateVisitor visitor) {
        visitor.visit(this);
        section.accept(visitor);
    }

    @Override
    public String toString() {
        return "page template '" + getId() + "'";
    }

}
