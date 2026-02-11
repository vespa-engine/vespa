// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.model;

import com.yahoo.component.provider.FreezableClass;
import com.yahoo.protect.Validator;

import java.util.*;

/**
 * A source mentioned in a page template.
 * <p>
 * Two sources are equal if they have the same name and parameters.
 *
 * @author bratseth
 */
public class Source extends FreezableClass implements PageElement {

    /** The "any" source - used to mark that any source is acceptable here */
    public static final Source any=new Source("*",true);

    /** The obligatory name of a source */
    private String name;

    private List<PageElement> renderers =new ArrayList<>();

    private Map<String,String> parameters =new LinkedHashMap<>();

    private String url;

    /** The precalculated hashCode of this object, or 0 if this is not frozen */
    private int hashCode=0;

    public Source(String name) {
        this(name,false);
    }

    /** Creates a source and optionally immediately freezes it */
    private Source(String name,boolean freeze) {
        setName(name);
        if (freeze)
            freeze();
    }

    /** Returns the name of this source (never null) */
    public String getName() { return name; }

    public final void setName(String name) {
        ensureNotFrozen();
        Validator.ensureNotNull("Source name",name);
        this.name=name;
    }

    /** Returns the url of this source or null if none */
    public String getUrl() { return url; }

    /**
     * Sets the url of this source. If a source has an url (i.e this returns non-null), the content of
     * the url is <i>not</i> fetched - fetching is left to the frontend by exposing this url in the result.
     */
    public void setUrl(String url) {
        ensureNotFrozen();
        this.url=url;
    }

    /**
     * Returns the renderers or choices of renderers to apply on individual items of this source
     * <p>
     * If this contains multiple renderers/choices, they are to be used on different types of hits returned by this source.
     */
    public List<PageElement> renderers() { return renderers; }

    /**
     * Returns the parameters of this source as a live reference (never null).
     * The parameters will be passed to the provider getting source data.
     */
    public Map<String,String> parameters() { return parameters; }

    @Override
    public void freeze() {
        if (isFrozen()) return;
        for (PageElement element : renderers) {
            if (element instanceof Renderer) {
                assignRendererForIfNotSet((Renderer)element);
            }
            else if (element instanceof Choice) {
                for (List<PageElement> renderersAlternative : ((Choice)element).alternatives()) {
                    for (PageElement rendererElement : renderersAlternative) {
                        Renderer renderer=(Renderer)rendererElement;
                        if (renderer.getRendererFor()==null)
                            renderer.setRendererFor(name);
                    }
                }
            }
            element.freeze();
        }
        parameters = Collections.unmodifiableMap(parameters);
        hashCode=hashCode();
        super.freeze();
    }

    private void assignRendererForIfNotSet(Renderer renderer) {
        if (renderer.getRendererFor()==null)
            renderer.setRendererFor(name);
    }

    /** Accepts a visitor to this structure */
    @Override
    public void accept(PageTemplateVisitor visitor) {
        visitor.visit(this);
        for (PageElement renderer : renderers)
            renderer.accept(visitor);
    }

    @Override
    public int hashCode() {
        if (isFrozen()) return hashCode;
        int hashCode=name.hashCode();
        int i=0;
        for (Map.Entry<String,String> parameter : parameters.entrySet())
            hashCode+=i*17*parameter.getKey().hashCode()+i*31*parameter.getValue().hashCode();
        return hashCode;
    }

    @Override
    public boolean equals(Object other) {
        if (other==this) return true;
        if (! (other instanceof Source)) return false;
        Source otherSource=(Source)other;
        if (! this.name.equals(otherSource.name)) return false;
        if (this.parameters.size() != otherSource.parameters.size()) return false;
        for (Map.Entry<String,String> thisParameter : this.parameters.entrySet())
            if ( ! thisParameter.getValue().equals(otherSource.parameters.get(thisParameter.getKey())))
                return false;
        return true;
    }

    @Override
    public String toString() {
        return "source '" + name + "'";
    }

}
