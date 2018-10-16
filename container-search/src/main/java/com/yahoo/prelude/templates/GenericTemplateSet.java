// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.templates;

import com.yahoo.protect.Validator;
import com.yahoo.search.Query;

import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Properties;

/**
 * Superclass of a set of templates for rendering (serializing) results
 * 
 * @deprecated use a renderer instead
 */
// TODO: Remove on Vespa 7
@Deprecated // OK (But wait for deprecated handlers in vespaclient-container-plugin to be removed)
public class GenericTemplateSet {

    public static final String  DEFAULT_MIMETYPE    = "text/xml";
    public static final String  DEFAULT_ENCODING    = "utf-8";

    /** Templates */
    private HashMap<String, Template<? extends Writer>> templates;

    /** The text MIME subtype this template returns, xml, plain or html */
    private String mimeType;

    /** The charset encoding this template should have */
    private String encoding;

    private String boldOpenTag = null;
    private String boldCloseTag = null;
    private String separatorTag = null;

    /**
     * Document summary class for this template
     */
    private String summaryClass = null;

    /**
     * The unique name of this template set
     */
    private final String name;

    /**
     * Creates a template set containing no templates
     */
    public GenericTemplateSet(String name, String mimeType, String encoding) {
        this.mimeType    = mimeType;
        this.encoding    = encoding;
        this.name = name;

        templates = new LinkedHashMap<>();
    }


    public String getName() {
        return name;
    }

    /**
     * Returns the text MIME
     */
    public String getMimeType() { return mimeType; }

    /**
     * Returns the text encoding
     */
    public String getEncoding() { return encoding; }

    /** Returns the encoding of the query, or the encoding given by the template if none is set */
    public final String getRequestedEncoding(Query query) {
        String encoding = query.getModel().getEncoding();
        if (encoding != null) return encoding;
        return getEncoding();
    }

    /**
     * Returns the selected template
     *
     * @return the template to use, never null
     */
    public Template<? extends Writer> getTemplate(String templateName) {
      return templates.get(templateName);
    }

    /**
     * Sets the selected template
     *
     * @throws NullPointerException if the given template is null
     */
    public void setTemplate(String templateName, Template<? extends Writer> template) {
      templates.put(templateName,template);
    }

    /**
     * Sets the selected template
     *
     * @throws NullPointerException if the given template is null
     */
    public void setTemplateNotNull(String templateName, Template<? extends Writer> template) {
        Validator.ensureNotNull("Template "+templateName,template);
        templates.put(templateName,template);
    }


    /**
     * Sets the highligting marks for this template
     *
     * @param start the highlingting start mark
     * @param end   the highlingting end mark
     * @param sep   the highlingting separator mark
     */
    public void setHighlightTags(String start, String end, String sep) {
        boldOpenTag = start;
        boldCloseTag = end;
        separatorTag = sep;
    }

    // may return null
    public String getBoldOpenTag() {
        return boldOpenTag;
    }

    // may return null
    public String getBoldCloseTag() {
        return boldCloseTag;
    }

    // may return null
    public String getSeparatorTag() {
        return separatorTag;
    }


    /**
     * Set the default summary class to use with this template.
     */
    public void setSummaryClass(String summaryClass) {
        this.summaryClass = summaryClass;
    }

    /**
     * Type safe accessor to get the default document summary class for this
     * template set.  This is also here to insulate the rest of the code
     * against changes in the naming of the properties in the property file.
     */
    public String getSummaryClass() {
        if (summaryClass != null && ! summaryClass.isEmpty()) {
            return summaryClass;
        } else {
            return null;
        }
    }

}
