// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.model;

import com.yahoo.component.provider.FreezableClass;
import com.yahoo.protect.Validator;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A description of a way to present data items from a source.
 * All data items has a default renderer. This can be overridden or parametrized by
 * an explicit renderer.
 *
 * @author bratseth
 */
public final class Renderer extends FreezableClass implements PageElement {

    private String name;

    private String rendererFor;

    private Map<String,String> parameters =new LinkedHashMap<>();

    public Renderer(String name) {
        setName(name);
    }

    /**
     * Returns the name of this renderer (never null).
     * The name should be recognized by the system receiving results for rendering
     */
    public String getName() { return name; }

    public final void setName(String name) {
        ensureNotFrozen();
        Validator.ensureNotNull("renderer name",name);
        this.name=name;
    }

    /**
     * Returns the name of the kind of data this is a renderer for.
     * This is used to allow frontends to dispatch the right data items (hits) to
     * the right renderer in the case where the data consists of a heterogeneous list.
     * <p>
     * This is null if this is a renderer for a whole section, or if this is a renderer
     * for all kinds of data from a particular source <i>and</i> this is not frozen.
     * <p>
     * Otherwise, it is either the name of the source this is the renderer for,
     * <i>or</i> the renderer for all data items having this name as a <i>type</i>.
     * <p>
     * This, a (frontend) dispatcher of data to renderers should for each data item:
     * <ul>
     *     <li>use the renderer having the same name as any <code>type</code> name set of the data item
     *     <li>if no such renderer, use the renderer having <code>rendererFor</code> equal to the data items <code>source</code>
     *     <li>if no such renderer, use a default renderer
     * </ul>
     */
    public String getRendererFor() { return rendererFor; }

    public void setRendererFor(String rendererFor) {
        ensureNotFrozen();
        this.rendererFor=rendererFor;
    }

    /**
     * Returns the parameters of this renderer as a live reference (never null).
     * The parameters will be passed to the renderer with each result
     */
    public Map<String,String> parameters() { return parameters; }

    @Override
    public void freeze() {
        if (isFrozen()) return;
        super.freeze();
        parameters = Collections.unmodifiableMap(parameters);
    }

    /** Accepts a visitor to this structure */
    @Override
    public void accept(PageTemplateVisitor visitor) {
        visitor.visit(this);
    }
     @Override
     public String toString() {
        return "renderer '" + name + "'";
    }

}
