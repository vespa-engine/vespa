// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.templates;

import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;
import com.yahoo.text.GenericWriter;
import com.yahoo.text.XMLWriter;

import java.io.IOException;
import java.io.Writer;

/**
 * <p>A template set contains instances of the various templates
 * required to render a result.</p>
 *
 * <p>Normal usage is to create an instance and populate it with templates,
 * but this class also supports subclassing to refine the behaviour,
 * like returning different templates for different hit types.</p>
 *
 * @author bratseth
 * @deprecated use a renderer instead
 */
@SuppressWarnings("deprecation")
@Deprecated // OK (But wait for deprecated handlers in vespaclient-container-plugin to be removed)
// TODO: Remove on Vespa 7
public class TemplateSet<T extends Writer> extends UserTemplate<T> {

    private static final String queryContextTemplateName = "queryContext";

    private static final DefaultTemplateSet defaultTemplateSet=new DefaultTemplateSet();

    /**
     * Creates a template set containing no templates
     *
     * @param name the unique name of this template set, used for
     * refering to it by clients
     */
    public TemplateSet(String name,
                       String mimeType,
                       String encoding) {
        super(name, mimeType,encoding);
    }

    /**
     * Returns the default template set. This is a template set which renders in
     * the default xml format
     */
    public static UserTemplate<XMLWriter> getDefault() {
        return defaultTemplateSet;
    }

    /**
     * Returns the result header template
     *
     * @param  result the result which will use the template
     * @return the template to use, never null
     */
    @SuppressWarnings("unchecked")
    public Template<T> getHeader(Result result) { return (Template<T>) getTemplate("header"); }

    /**
     * Sets the header template
     *
     * @param header the template to use for rendering getHeaders
     * @throws NullPointerException if the given template is null
     */
    public void setHeader(Template<T> header) {
        setTemplateNotNull("header",header);
    }

    /**
     * Returns the result footer template
     *
     * @param  result the result which will use the template
     * @return the template to use, never null
     */
    @SuppressWarnings("unchecked")
    public Template<T> getFooter(Result result) { return (Template<T>) getTemplate("footer"); }

    /**
     * Sets the footer template
     *
     * @param footer the template to use for rendering footers
     * @throws NullPointerException if the given template is null
     */
    public void setFooter(Template<T> footer) {
        setTemplateNotNull("footer",footer);
    }

    /**
     * Returns the empty body template
     *
     * @param  result the result which will use the template
     * @return the template to use, never null
     */
    @SuppressWarnings("unchecked")
    public Template<T> getNohits(Result result) { return (Template<T>) getTemplate("nohits"); }


    /**
     * @return the template for rendering the query context, never null
     */
    @SuppressWarnings("unchecked")
    public Template<T> getQueryContext(Result result) {
        return (Template<T>) getTemplate(queryContextTemplateName);
    }

    /**
     * @param template The template to be used for rendering query contexts, never null.
     */
    public void setQueryContext(Template<T> template) {
        setTemplateNotNull(queryContextTemplateName, template);
    }

    /**
     * Sets the nohits template
     *
     * @param nohits the template to use for rendering empty results
     * @throws NullPointerException if the given template is null
     */
    public void setNohits(Template<T> nohits) {
        setTemplateNotNull("nohits",nohits);
    }

    /**
     * Returns the error body template
     *
     * @param  result the result which will use the template
     * @return the template to use, never null
     */
    @SuppressWarnings("unchecked")
    public Template<T> getError(Result result) { return (Template<T>) getTemplate("error"); }

    /**
     * Sets the error template
     *
     * @param error the template to use for rendering errors
     * @throws NullPointerException if the given template is null
     */
    public void setError(Template<T> error) {
        setTemplateNotNull("error",error);
    }

    /**
     * Returns the hit template
     *
     * @param  resultHit the hit which will use the template
     * @return the template to use, never null
     */
    @SuppressWarnings("unchecked")
    public Template<T> getHit(Hit resultHit) { return (Template<T>) getTemplate("hit"); }

    /**
     * Sets the hit template
     *
     * @param hit the template to use for rendering hits
     * @throws NullPointerException if the given template is null
     */
    public void setHit(Template<T> hit) {
        setTemplateNotNull("hit",hit);
    }

    /**
     * Returns the hit footer template
     *
     * @param  hit the hit which will use the template
     * @return the template to use, or null if no hit footer is used
     */
    @SuppressWarnings("unchecked")
    public Template<T> getHitFooter(Hit hit) { return (Template<T>) getTemplate("hitfooter"); }

    public String toString() {
        return "template set " + getName() + " of type " + getMimeType() +
            " [header=" + getTemplate("header") +
            ",footer=" + getTemplate("footer") +
            ",nohits=" + getTemplate("nohits") +
            ",error=" + getTemplate("error") +
            ",hit=" + getTemplate("hit") + "]";
    }

    @Override
    public void header(Context context, T writer) throws IOException {
        getHeader(null).render(context, writer);
    }

    @Override
    public void footer(Context context, T writer) throws IOException {
        getFooter(null).render(context, writer);
    }

    @Override
    public void hit(Context context, T writer) throws IOException {
        getHit(null).render(context, writer);
    }

    @Override
    public void error(Context context, T writer) throws IOException {
        getError(null).render(context, writer);
    }

    @Override
    public void hitFooter(Context context, T writer) throws IOException {
        Template<T> hitFooter = getHitFooter(null);
        if (hitFooter != null)
            hitFooter.render(context, writer);
    }

    @Override
    public void noHits(Context context, T writer) throws IOException {
        getNohits(null).render(context, writer);
    }

    @Override
    public void queryContext(Context context, T writer) throws IOException {
        getQueryContext(null).render(context, writer);
    }

}
