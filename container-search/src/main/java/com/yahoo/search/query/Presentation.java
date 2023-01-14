// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.google.common.base.Splitter;
import com.yahoo.collections.LazySet;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.prelude.query.Highlight;
import com.yahoo.prelude.query.IndexedItem;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileFieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.ranking.MatchPhase;
import com.yahoo.search.rendering.RendererRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Parameters deciding how the result of a query should be presented
 *
 * @author Arne Bergene Fossaa
 */
public class Presentation implements Cloneable {

    /** The type representing the property arguments consumed by this */
    private static final QueryProfileType argumentType;

    public static final String PRESENTATION = "presentation";
    public static final String BOLDING = "bolding";
    public static final String TIMING = "timing";
    public static final String SUMMARY = "summary";
    public static final String SUMMARY_FIELDS = "summaryFields";
    public static final String TENSORS = "tensors";

    /** The (short) name of the parameter holding the name of the return format to use */
    public static final String FORMAT = "format";

    static {
        argumentType = new QueryProfileType(PRESENTATION);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(BOLDING, "boolean", "bolding"));
        argumentType.addField(new FieldDescription(TIMING, "boolean", "timing"));
        argumentType.addField(new FieldDescription(SUMMARY, "string", "summary"));
        argumentType.addField(new FieldDescription(SUMMARY_FIELDS, "string", "summaryFields"));
        QueryProfileType formatArgumentType = new QueryProfileType(FORMAT);
        formatArgumentType.setBuiltin(true);
        formatArgumentType.setStrict(true);
        formatArgumentType.addField(new FieldDescription("", "string", "format template"));
        formatArgumentType.addField(new FieldDescription(TENSORS, "string", "format.tensors"));
        formatArgumentType.freeze();
        argumentType.addField(new FieldDescription(FORMAT, new QueryProfileFieldType(formatArgumentType), "format"));
        argumentType.freeze();
    }
    public static QueryProfileType getArgumentType() { return argumentType; }

    /** How the result should be highlighted */
    private Highlight highlight = null;

    /** The terms to highlight in the result (only used by BoldingSearcher, may be removed later). */
    private List<IndexedItem> boldingData = null;

    /** Whether to do highlighting */
    private boolean bolding = true;

    /** The summary class to be shown */
    private String summary = null;

    /** The name of the renderer to use for rendering the hits. */
    private ComponentSpecification format = RendererRegistry.defaultRendererId.toSpecification();

    /** Whether optional timing data should be rendered */
    private boolean timing = false;

    /** Whether to renders tensors in short form */
    private boolean tensorShortForm = true;

    /** Whether to renders tensors in short form */
    private boolean tensorDirectValues = false; // TODO: Flip default on Vespa 9

    /** Set of explicitly requested summary fields, instead of summary classes */
    private Set<String> summaryFields = LazySet.newHashSet();

    private static final Splitter COMMA_SPLITTER = Splitter.on(',').omitEmptyStrings().trimResults();

    public Presentation(Query parent) { }

    /** Returns how terms in this result should be highlighted, or null if not set */
    public Highlight getHighlight() { return highlight; }

    /** Sets how terms in this result should be highlighted. Set to null to turn highlighting off */
    public void setHighlight(Highlight highlight) { this.highlight = highlight; }

    /** Returns the name of the summary class to be used to present hits from this query, or null if not set */
    public String getSummary() { return summary; }

    /** Sets the name of the summary class to be used to present hits from this query */
    public void setSummary(String summary) { this.summary = summary; }

    /** Returns whether matching query terms should be bolded in the result. Default is true. */
    public boolean getBolding() { return bolding; }

    /** Sets whether matching query terms should be bolded in the result */
    public void setBolding(boolean bolding) { this.bolding = bolding; }

    /** Get the name of the format desired for result rendering. */
    public ComponentSpecification getRenderer() { return format; }

    /** Set the desired format for result rendering. If null, use the default renderer. */
    public void setRenderer(ComponentSpecification format) {
        this.format = (format != null) ? format : RendererRegistry.defaultRendererId.toSpecification();
    }

    /** Get the name of the format desired for result rendering. */
    public String getFormat() { return format.getName(); }

    /** Set the desired format for result rendering. If null, use the default renderer. */
    public void setFormat(String format) {
        setRenderer(ComponentSpecification.fromString(format));
    }

    @Override
    public Object clone()  {
        try {
            Presentation clone = (Presentation)super.clone();
            if (boldingData != null)
                clone.boldingData = new ArrayList<>(boldingData);

            if (highlight != null)
                clone.highlight = highlight.clone();

            if (summaryFields != null) {
                clone.summaryFields = LazySet.newHashSet();
                clone.summaryFields.addAll(this.summaryFields);
            }

            return clone;
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Someone inserted a noncloneable superclass", e);
        }
    }

    /** Returns whether to add optional timing data to the rendered result. */
    public boolean getTiming() {
        return timing;
    }

    public void setTiming(boolean timing) {
        this.timing = timing;
    }

    /**
     * Return the set of explicitly requested fields. Returns an empty set if no
     * fields are specified outside of summary classes. The returned set is
     * mutable and fields may be added or removed before passing on the query.
     *
     * @return the set of names of requested fields, never null
     */
    public Set<String> getSummaryFields() {
        return summaryFields;
    }

    /**
     * Parse the given string as a comma delimited set of field names and
     * overwrite the set of summary fields. Whitespace will be trimmed. If you
     * want to add or remove fields programmatically, use
     * {@link #getSummaryFields()} and modify the returned set.
     *
     * @param asString the summary fields requested, e.g. "price,author,title"
     */
    public void setSummaryFields(String asString) {
        summaryFields.clear();
        for (String field : COMMA_SPLITTER.split(asString)) {
            summaryFields.add(field);
        }

    }

    /**
     * Returns whether tensors should use short form in JSON and textual representations, see
     * <a href="https://docs.vespa.ai/en/reference/document-json-format.html#tensor">https://docs.vespa.ai/en/reference/document-json-format.html#tensor</a>.
     * Default is true.
     */
    public boolean getTensorShortForm() { return tensorShortForm; }

    /** @deprecated use setTensorFormat(). */
    @Deprecated // TODO: Remove on Vespa 9
    public void setTensorShortForm(String value) {
        setTensorFormat(value);
    }
    /**
     * Sets whether tensors should use short form in JSON and textual representations from a string.
     *
     * @param value a string which must be either 'short' or 'long'
     * @throws IllegalArgumentException if any other value is passed
     */
    public void setTensorFormat(String value) {
        switch (value) {
            case "short" :
                tensorShortForm = true;
                tensorDirectValues = false;
                break;
            case "long" :
                tensorShortForm = false;
                tensorDirectValues = false;
                break;
            case "short-value" :
                tensorShortForm = true;
                tensorDirectValues = true;
                break;
            case "long-value" :
                tensorShortForm = false;
                tensorDirectValues = true;
                break;
            default : throw new IllegalArgumentException("Value must be 'long', 'short', 'long-value', or 'short-value', not '" + value + "'");
        };
    }

    public void setTensorShortForm(boolean tensorShortForm) {
        this.tensorShortForm = tensorShortForm;
    }

    /**
     * Returns whether tensor content should be rendered directly, or inside a JSON object containing a
     * "type" entry having the tensor type, and a "cells"/"values"/"blocks" entry (depending on type),
     * having the tensor content. See
     * <a href="https://docs.vespa.ai/en/reference/document-json-format.html#tensor">https://docs.vespa.ai/en/reference/document-json-format.html#tensor</a>.
     * Default is false: Render wrapped in a JSON object.
     */
    public boolean getTensorDirectValues() { return tensorDirectValues; }

    public void setTensorDirectValues(boolean tensorDirectValues) {
        this.tensorDirectValues = tensorDirectValues;
    }

    /** Prepares this for binary serialization. For internal use - see {@link Query#prepare} */
    public void prepare() {
        if (highlight != null)
            highlight.prepare();
    }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof Presentation p)) return false;
        return QueryHelper.equals(bolding, p.bolding) && QueryHelper.equals(summary, p.summary);
    }

    @Override
    public int hashCode() {
        return QueryHelper.combineHash(bolding, summary);
    }

}

