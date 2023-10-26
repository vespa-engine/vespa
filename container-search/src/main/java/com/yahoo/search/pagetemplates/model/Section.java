// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.model;

import com.yahoo.component.provider.FreezableClass;
import com.yahoo.search.query.Sorting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An element of a page template corresponding to a physical area of the layout of the final physical page.
 * Pages are freezable - once frozen calling a setter will cause an IllegalStateException, and returned
 * live collection references are unmodifiable
 *
 * @author bratseth
 */
public class Section extends FreezableClass implements PageElement {

    private final String id;

    private Layout layout=Layout.column;

    private String region;

    /** The elements of this - sources, subsections etc. and/or choices of the same */
    private List<PageElement> elements=new ArrayList<>();

    /** Filtered versions of elements pre-calculated at freeze time */
    private List<PageElement> sections, sources, renderers;

    private int max=-1;

    private int min=-1;

    private Sorting order=null;

    private static AtomicInteger nextId=new AtomicInteger();

    public Section() {
        this(null);
    }

    /** Creates a section with an id (or null if no id) */
    public Section(String id) {
        if (id==null || id.isEmpty())
            this.id=String.valueOf("section_" + nextId.incrementAndGet());
        else
            this.id=id;
    }

    /** Returns a unique id of this section within the page. Used for referencing and identification. Never null. */
    public String getId() { return id; }

    /**
     * Returns the layout identifier describing the kind of layout which should be used by the rendering engine to
     * lay out the content of this section. This is never null. Default: "column".
     */
    public Layout getLayout() { return layout; }

    /** Sets the layout. If the layout is set to null it will become Layout.column */
    public void setLayout(Layout layout) {
        ensureNotFrozen();
        if (layout==null) layout=Layout.column;
        this.layout=layout;
    }

    /**
     * Returns the identifier telling the layout of the containing section where this section should be placed.
     * Permissible values, and whether this is mandatory is determined by the particular layout identifier of the parent.
     * May be null if a placement is not required by the containing layout, or if this is the top-level section.
     * This is null by default.
     */
    public String getRegion() { return region; }

    public void setRegion(String region) {
        ensureNotFrozen();
        this.region=region;
    }

    /**
     * Returns the elements of this - sources, subsections and presentations and/or choices of these,
     * as a live reference which can be modified to change the content of this (unless this is frozen).
     * <p>
     * All elements are kept in a single list to allow multiple elements of each type to be nested within separate
     * choices, and to maintain the internal order of elements of various types, which is sometimes significant.
     * To extract a certain kind of elements (say, sources), the element list must be traversed to collect
     * all source elements as well as all choices of sources.
     * <p>
     * This list is never null but may be empty.
     */
    public List<PageElement> elements() { return elements; }

    /**
     * Convenience method which returns the elements <b>and choices</b> of the given type in elements as a
     * read-only list. Not that as this returns both concrete elements and choices betwen them,
     * the list element cannot be case to the given class - this must be used in conjunction
     * with a resolve which contains the resolution to the choices.
     *
     * @param pageTemplateModelElementClass type to returns elements and choices of, a subtype of PageElement
     */
    public List<PageElement> elements(@SuppressWarnings("rawtypes") Class pageTemplateModelElementClass) {
        if (isFrozen()) { // Use precalculated lists
            if (pageTemplateModelElementClass==Section.class)
                return sections;
            else if (pageTemplateModelElementClass==Source.class)
                return sources;
            else if (pageTemplateModelElementClass==Renderer.class)
                return renderers;
        }
        return createElementList(pageTemplateModelElementClass);
    }

    @SuppressWarnings("unchecked")
    private List<PageElement> createElementList(@SuppressWarnings("rawtypes") Class pageTemplateModelElementClass) {
        List<PageElement> filteredElements=new ArrayList<>();
        for (PageElement element : elements) {
            if (pageTemplateModelElementClass.isAssignableFrom(element.getClass()))
                filteredElements.add(element);
            else if (element instanceof AbstractChoice)
                if (((AbstractChoice)element).isChoiceBetween(pageTemplateModelElementClass))
                    filteredElements.add(element);
        }
        return Collections.unmodifiableList(filteredElements);
    }

    /** Returns the choice of ways to sort immediate children in this, or empty meaning sort by default order (relevance) */
    public Sorting getOrder() { return order; }

    public void setOrder(Sorting order) {
        ensureNotFrozen();
        this.order=order;
    }

    /** Returns max number of (immediate) elements/sections permissible within this, -1 means unrestricted. Default: -1. */
    public int getMax() { return max; }

    public void setMax(int max) {
        ensureNotFrozen();
        this.max=max;
    }

    /** Returns min number of (immediate) elements/sections desired within this, -1 means unrestricted. Default: -1. */
    public int getMin() { return min; }

    public void setMin(int min) {
        ensureNotFrozen();
        this.min=min;
    }

    @Override
    public void freeze() {
        if (isFrozen()) return;

        for (PageElement element : elements)
            element.freeze();
        elements=Collections.unmodifiableList(elements);
        sections=createElementList(Section.class);
        sources=createElementList(Source.class);
        renderers=createElementList(Renderer.class);

        super.freeze();
    }

    /** Accepts a visitor to this structure */
    @Override
    public void accept(PageTemplateVisitor visitor) {
        visitor.visit(this);
        for (PageElement element : elements)
            element.accept(visitor);
    }

    @Override
    public String toString() {
        if (id==null || id.isEmpty()) return "a section";
        return "section '" + id + "'";
    }

}
