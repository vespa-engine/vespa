// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.templates;

import com.yahoo.concurrent.CopyOnWriteHashMap;
import com.yahoo.io.ByteWriter;
import com.yahoo.net.URI;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.hitfield.HitField;
import com.yahoo.prelude.hitfield.JSONString;
import com.yahoo.prelude.hitfield.XMLString;
import com.yahoo.search.Result;
import com.yahoo.search.grouping.result.HitRenderer;
import com.yahoo.search.result.*;
import com.yahoo.text.Utf8String;
import com.yahoo.text.XML;
import com.yahoo.text.XMLWriter;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.Map;

/**
 * <p>A template set which provides XML rendering of results and hits.</p>
 *
 * <p>This can be extended to create custom programmatic templates.
 * Create a subclass which has static inner classes extending DefaultTemplate for the templates
 * you wish to override and call the set method for those templates in the subclass template set
 * constructor. Some of the default templates contained utility functions, and can be overridden
 * in place of DefaultTemplate to gain access to these. See TiledTemplateSet for an example.</p>
 *
 * @author bratseth
 * @deprecated use JsonRenderer instead
 */
@SuppressWarnings("deprecation")
@Deprecated // TODO: Remove on Vespa 7
public class DefaultTemplateSet extends UserTemplate<XMLWriter> {

    private static final Utf8String RESULT = new Utf8String("result");
    private static final Utf8String GROUP = new Utf8String("group");
    private static final Utf8String ID = new Utf8String("id");
    private static final Utf8String FIELD = new Utf8String("field");
    private static final Utf8String HIT = new Utf8String("hit");
    private static final Utf8String ERROR = new Utf8String("error");
    private static final Utf8String TOTAL_HIT_COUNT = new Utf8String("total-hit-count");
    private static final Utf8String QUERY_TIME = new Utf8String("querytime");
    private static final Utf8String SUMMARY_FETCH_TIME = new Utf8String("summaryfetchtime");
    private static final Utf8String SEARCH_TIME = new Utf8String("searchtime");
    private static final Utf8String NAME = new Utf8String("name");
    private static final Utf8String CODE = new Utf8String("code");
    private static final Utf8String COVERAGE_DOCS = new Utf8String("coverage-docs");
    private static final Utf8String COVERAGE_NODES = new Utf8String("coverage-nodes");
    private static final Utf8String COVERAGE_FULL = new Utf8String("coverage-full");
    private static final Utf8String COVERAGE = new Utf8String("coverage");
    private static final Utf8String RESULTS_FULL = new Utf8String("results-full");
    private static final Utf8String RESULTS = new Utf8String("results");
    private static final Utf8String TYPE = new Utf8String("type");
    private static final Utf8String RELEVANCY = new Utf8String("relevancy");
    private static final Utf8String SOURCE = new Utf8String("source");

    private final CopyOnWriteHashMap<String, Utf8String> fieldNameMap = new CopyOnWriteHashMap<>();


    /**
     * Create a template set with a name. This will be initialized with the default templates -
     * use the set methods from the subclass constructor to override any of these with other template classes.
     */
    protected DefaultTemplateSet(String name) {
        super(name,
              DEFAULT_MIMETYPE,
              DEFAULT_ENCODING
        );
    }

    public DefaultTemplateSet() {
        this("default");
    }

    /** Uses an XML writer in this template */
    @Override
    public XMLWriter wrapWriter(Writer writer) {
        return XMLWriter.from(writer, 10, -1);
    }

    @Override
    public void header(Context context, XMLWriter writer) throws IOException {
        Result result=(Result)context.get("result");
        // TODO: move setting this to Result
        context.setUtf8Output("utf-8".equalsIgnoreCase(getRequestedEncoding(result.getQuery())));
        writer.xmlHeader(getRequestedEncoding(result.getQuery()));
        writer.openTag(RESULT).attribute(TOTAL_HIT_COUNT,String.valueOf(result.getTotalHitCount()));
        renderCoverageAttributes(result.getCoverage(false), writer);
        renderTime(writer, result);
        writer.closeStartTag();
    }

    private void renderTime(final XMLWriter writer, final Result result) {
        if (!result.getQuery().getPresentation().getTiming()) {
            return;
        }

        final String threeDecimals = "%.3f";
        final double milli = .001d;
        final long now = System.currentTimeMillis();
        final long searchTime = now - result.getElapsedTime().first();
        final double searchSeconds = ((double) searchTime) * milli;

        if (result.getElapsedTime().firstFill() != 0L) {
            final long queryTime = result.getElapsedTime().weightedSearchTime();
            final long summaryFetchTime = result.getElapsedTime().weightedFillTime();
            final double querySeconds = ((double) queryTime) * milli;
            final double summarySeconds = ((double) summaryFetchTime) * milli;
            writer.attribute(QUERY_TIME, String.format(threeDecimals, querySeconds));
            writer.attribute(SUMMARY_FETCH_TIME, String.format(threeDecimals, summarySeconds));
        }
        writer.attribute(SEARCH_TIME, String.format(threeDecimals, searchSeconds));
    }

    @Override
    public void footer(Context context, XMLWriter writer) throws IOException {
        writer.closeTag();
    }

    @Override
    /**
     * Renders the header of a hit.<br/>
     * Post-condition: The hit tag is open in this XML writer
     */
    public void hit(Context context, XMLWriter writer) throws IOException {
        Hit hit=(Hit)context.get("hit");

        if (hit instanceof HitGroup) {
            renderHitGroup((HitGroup) hit, context, writer);
        } else {
            writer.openTag(HIT);
            renderHitAttributes(hit,writer);
            writer.closeStartTag();
            renderHitFields(context, hit, writer);
        }
    }


    @Override
    /**
     * Renders the footer of a hit.
     *
     * Pre-condition: The hit tag is open in this XML writer.<br/>
     * Post-condition: The hit tag is closed
     */
    public void hitFooter(Context context, XMLWriter writer) throws IOException {
        writer.closeTag();
    }

    @Override
    public void error(Context context, XMLWriter writer) {
        ErrorMessage error=((Result)context.get("result")).hits().getError();
        writer.openTag(ERROR).attribute(CODE,error.getCode()).content(error.getMessage(),false).closeTag();
    }

    @Override
    public void noHits(Context context, XMLWriter writer) {
        // no hits, do nothing :)
    }

    protected static void renderCoverageAttributes(Coverage coverage, XMLWriter writer) {
        if (coverage == null) return;
        writer.attribute(COVERAGE_DOCS,coverage.getDocs());
        writer.attribute(COVERAGE_NODES,coverage.getNodes());
        writer.attribute(COVERAGE_FULL,coverage.getFull());
        writer.attribute(COVERAGE,coverage.getResultPercentage());
        writer.attribute(RESULTS_FULL,coverage.getFullResultSets());
        writer.attribute(RESULTS,coverage.getResultSets());
    }

    /**
     * Writes a hit's default attributes like 'type', 'source', 'relevancy'.
     */
    protected void renderHitAttributes(Hit hit,XMLWriter writer) throws IOException {
        writer.attribute(TYPE,hit.getTypeString());
    	if (hit.getRelevance() != null) {
            writer.attribute(RELEVANCY, hit.getRelevance().toString());
        }
        writer.attribute(SOURCE, hit.getSource());
    }

    /** Opens (but does not close) the group hit tag */
    protected void renderHitGroup(HitGroup hit, Context context, XMLWriter writer) throws IOException {
        if (HitRenderer.renderHeader(hit, writer)) {
            // empty
        } else if (hit.types().contains("grouphit")) {
            // TODO Keep this?
            renderHitGroupOfTypeGroupHit(context, hit, writer);
        } else {
            renderGroup(hit, writer);
        }
    }


    /**
     * Renders a hit group.
     */
    protected void renderGroup(HitGroup hit, XMLWriter writer) throws IOException {
        writer.openTag(GROUP);
        renderHitAttributes(hit, writer);
        writer.closeStartTag();
    }

    // Can't name this renderGroupHit as GroupHit is a class having nothing to do with HitGroup.
    // Confused yet? Good!
    protected void renderHitGroupOfTypeGroupHit(Context context, HitGroup hit, XMLWriter writer) throws IOException {
        writer.openTag(HIT);
        renderHitAttributes(hit, writer);
        renderId(hit.getId(), writer);
        writer.closeStartTag();
    }


    protected void renderId(URI uri, XMLWriter writer) {
        if (uri != null) {
            writer.openTag(ID).content(uri.stringValue(),false).closeTag();
        }
    }

    /**
     * Renders all fields of a hit.
     * Simply calls {@link #renderField(Context, Hit, java.util.Map.Entry, XMLWriter)} for every field.
     */
    protected void renderHitFields(Context context, Hit hit, XMLWriter writer) throws IOException {
        renderSyntheticRelevancyField(hit, writer);
        for (Iterator<Map.Entry<String, Object>> it = hit.fieldIterator(); it.hasNext(); ) {
            renderField(context, hit, it.next(), writer);
        }
    }

    private void renderSyntheticRelevancyField(Hit hit, XMLWriter writer) {
        final String relevancyFieldName = "relevancy";
        final Relevance relevance = hit.getRelevance();

        if (shouldRenderField(hit, relevancyFieldName) && relevance != null) {
            renderSimpleField(relevancyFieldName, relevance, writer);
        }
    }

    protected void renderField(Context context, Hit hit, Map.Entry<String, Object> entry, XMLWriter writer) throws IOException {
        String fieldName = entry.getKey();

        if (!shouldRenderField(hit, fieldName)) return;
        if (fieldName.startsWith("$")) return; // Don't render fields that start with $ // TODO: Move to should render

        writeOpenFieldElement(fieldName, writer);
        renderFieldContent(context, hit, fieldName, writer);
        writeCloseFieldElement(writer);
    }

    private void writeOpenFieldElement(String fieldName, XMLWriter writer) {
        Utf8String utf8 = fieldNameMap.get(fieldName);
        if (utf8 == null) {
            utf8 = new Utf8String(fieldName);
            fieldNameMap.put(fieldName, utf8);
        }
        writer.openTag(FIELD).attribute(NAME, utf8);
        writer.closeStartTag();
    }

    private void writeCloseFieldElement(XMLWriter writer) {
        writer.closeTag();
    }

    protected void renderFieldContent(Context context, Hit hit, String name, XMLWriter writer) {
        writer.escapedContent(asXML(hit.getField(name)), false);
    }

    private String asXML(Object value) {
        if (value == null)
            return "(null)";
        else if (value instanceof HitField)
            return ((HitField)value).quotedContent(false);
        else if (value instanceof StructuredData || value instanceof XMLString || value instanceof JSONString)
            return value.toString();
        else
            return XML.xmlEscape(value.toString(), false, '\u001f');
    }

    private void renderSimpleField(String fieldName, Object fieldValue, XMLWriter writer) {
        writeOpenFieldElement(fieldName, writer);
        writer.content(fieldValue.toString(),false);
        writeCloseFieldElement(writer);
    }

    /** Returns whether a field should be rendered. This default implementation always returns true */
    protected boolean shouldRenderField(Hit hit, String fieldName) {
        // skip depending on hit type
        return true;
    }



}
