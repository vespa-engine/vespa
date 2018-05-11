// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.rendering;

import com.fasterxml.jackson.core.JsonFactory;
import com.yahoo.concurrent.CopyOnWriteHashMap;
import com.yahoo.io.ByteWriter;
import com.yahoo.net.URI;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.GroupingListHit;
import com.yahoo.prelude.hitfield.HitField;
import com.yahoo.prelude.hitfield.JSONString;
import com.yahoo.prelude.hitfield.XMLString;
import com.yahoo.prelude.templates.UserTemplate;
import com.yahoo.processing.rendering.AsynchronousSectionedRenderer;
import com.yahoo.processing.response.Data;
import com.yahoo.processing.response.DataList;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.grouping.result.HitRenderer;
import com.yahoo.search.query.context.QueryContext;
import com.yahoo.search.result.*;
import com.yahoo.text.Utf8String;
import com.yahoo.text.XML;
import com.yahoo.text.XMLWriter;
import com.yahoo.yolean.trace.TraceNode;
import com.yahoo.yolean.trace.TraceVisitor;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

// TODO: Rename to XmlRenderer and make this a deprecated empty subclass.

/**
 * XML rendering of search results. This is NOT the default (but it once was).
 *
 * @author tonytv
 * @deprecated use JsonRenderer instead
 */
@SuppressWarnings({ "rawtypes", "deprecation" })
@Deprecated // TODO: Remove on Vespa 7
public final class DefaultRenderer extends AsynchronousSectionedRenderer<Result> {

    public static final String  DEFAULT_MIMETYPE    = "text/xml";
    public static final String  DEFAULT_ENCODING    = "utf-8";

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


    // this is shared between umpteen threads by design
    private final CopyOnWriteHashMap<String, Utf8String> fieldNameMap = new CopyOnWriteHashMap<>();

    private XMLWriter writer;

    public DefaultRenderer() {
        this(null);
    }

    /**
     * Creates a json renderer using a custom executor.
     * Using a custom executor is useful for tests to avoid creating new threads for each renderer registry.
     */
    public DefaultRenderer(Executor executor) {
        super(executor);
    }

    @Override
    public void init() {
        super.init();
        writer = null;
    }

    @Override
    public String getEncoding() {
        if (getResult() == null
                || getResult().getQuery() == null
                || getResult().getQuery().getModel().getEncoding() == null) {
            return DEFAULT_ENCODING;
        } else {
            return getResult().getQuery().getModel().getEncoding();
        }
    }

    @Override
    public String getMimeType() {
        return DEFAULT_MIMETYPE;
    }

    private XMLWriter wrapWriter(Writer writer) {
        return XMLWriter.from(writer, 10, -1);
    }

    private void header(XMLWriter writer, Result result) throws IOException {
        // TODO: move setting this to Result
        writer.xmlHeader(getRequestedEncoding(result.getQuery()));
        writer.openTag(RESULT).attribute(TOTAL_HIT_COUNT, String.valueOf(result.getTotalHitCount()));
        renderCoverageAttributes(result.getCoverage(false), writer);
        renderTime(writer, result);
        writer.closeStartTag();
    }

    private void renderTime(XMLWriter writer, Result result) {
        if ( ! result.getQuery().getPresentation().getTiming()) return;

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

    protected static void renderCoverageAttributes(Coverage coverage, XMLWriter writer) throws IOException {
        if (coverage == null) return;
        writer.attribute(COVERAGE_DOCS,coverage.getDocs());
        writer.attribute(COVERAGE_NODES,coverage.getNodes());
        writer.attribute(COVERAGE_FULL,coverage.getFull());
        writer.attribute(COVERAGE,coverage.getResultPercentage());
        writer.attribute(RESULTS_FULL,coverage.getFullResultSets());
        writer.attribute(RESULTS,coverage.getResultSets());
    }

    public void error(XMLWriter writer, Result result) throws IOException {
        ErrorMessage error = result.hits().getError();
        writer.openTag(ERROR).attribute(CODE,error.getCode()).content(error.getMessage(),false).closeTag();
    }

    @SuppressWarnings("UnusedParameters")
    protected void emptyResult(XMLWriter writer, Result result) throws IOException {}

    @SuppressWarnings("UnusedParameters")
    public void queryContext(XMLWriter writer, QueryContext queryContext, Query owner) throws IOException {
        if (owner.getTraceLevel()!=0) {
            XMLWriter xmlWriter=XMLWriter.from(writer);
            xmlWriter.openTag("meta").attribute("type", QueryContext.ID);
            TraceNode traceRoot = owner.getModel().getExecution().trace().traceNode().root();
            traceRoot.accept(new RenderingVisitor(xmlWriter, owner.getStartTime()));
            xmlWriter.closeTag();
        }
    }

    private void renderSingularHit(XMLWriter writer, Hit hit) {
        writer.openTag(HIT);
        renderHitAttributes(writer, hit);
        writer.closeStartTag();
        renderHitFields(writer, hit);
    }

    private void renderHitFields(XMLWriter writer, Hit hit) {
        renderSyntheticRelevanceField(writer, hit);
        for (Iterator<Map.Entry<String, Object>> it = hit.fieldIterator(); it.hasNext(); ) {
            renderField(writer, hit, it);
        }
    }

    private void renderField(XMLWriter writer, Hit hit, Iterator<Map.Entry<String, Object>> it) {
        renderGenericField(writer, hit, it.next());
    }

    private void renderGenericField(XMLWriter writer, Hit hit, Map.Entry<String, Object> entry) {
        String fieldName = entry.getKey();

        // skip depending on hit type
        if (fieldName.startsWith("$")) return; // Don't render fields that start with $ // TODO: Move to should render

        writeOpenFieldElement(writer, fieldName);
        renderFieldContent(writer, hit, fieldName);
        writeCloseFieldElement(writer);
    }

    private void renderFieldContent(XMLWriter writer, Hit hit, String fieldName) {
        writer.escapedContent(asXML(hit.getField(fieldName)), false);
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

    private void renderSyntheticRelevanceField(XMLWriter writer, Hit hit) {
        String relevancyFieldName = "relevancy";
        Relevance relevance = hit.getRelevance();

        // skip depending on hit type
        if (relevance != null) {
            renderSimpleField(writer, relevancyFieldName, relevance);
        }
    }

    private void renderSimpleField(XMLWriter writer, String relevancyFieldName, Relevance relevance) {
        writeOpenFieldElement(writer, relevancyFieldName);
        writer.content(relevance.toString(), false);
        writeCloseFieldElement(writer);
    }

    private void writeCloseFieldElement(XMLWriter writer) {
        writer.closeTag();
    }

    private void writeOpenFieldElement(XMLWriter writer, String relevancyFieldName) {
        Utf8String utf8 = fieldNameMap.get(relevancyFieldName);
        if (utf8 == null) {
            utf8 = new Utf8String(relevancyFieldName);
            fieldNameMap.put(relevancyFieldName, utf8);
        }
        writer.openTag(FIELD).attribute(NAME, utf8);
        writer.closeStartTag();
    }

    private void renderHitAttributes(XMLWriter writer, Hit hit) {
        writer.attribute(TYPE, hit.types().stream().collect(Collectors.joining(" ")));
        if (hit.getRelevance() != null)
            writer.attribute(RELEVANCY, hit.getRelevance().toString());
        writer.attribute(SOURCE, hit.getSource());
    }

    private void renderHitGroup(XMLWriter writer, HitGroup hit) throws IOException {
        if (HitRenderer.renderHeader(hit, writer)) {
            // empty
        } else if (hit.types().contains("grouphit")) {
            // TODO Keep this?
            renderHitGroupOfTypeGroupHit(writer, hit);
        } else {
            renderGroup(writer, hit);
        }
    }

    private void renderGroup(XMLWriter writer, HitGroup hit) {
        writer.openTag(GROUP);
        renderHitAttributes(writer, hit);
        writer.closeStartTag();
    }

    private void renderHitGroupOfTypeGroupHit(XMLWriter writer, HitGroup hit) {
        writer.openTag(HIT);
        renderHitAttributes(writer, hit);
        renderId(writer, hit);
        writer.closeStartTag();
    }

    private void renderId(XMLWriter writer, HitGroup hit) {
        URI uri = hit.getId();
        if (uri != null) {
            writer.openTag(ID).content(uri.stringValue(),false).closeTag();
        }
    }

    private boolean simpleRenderHit(XMLWriter writer, Hit hit) throws IOException {
        if (hit instanceof DefaultErrorHit) {
            return simpleRenderDefaultErrorHit(writer, (DefaultErrorHit) hit);
        } else if (hit instanceof GroupingListHit) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean simpleRenderDefaultErrorHit(XMLWriter writer, ErrorHit defaultErrorHit) throws IOException {
        writer.openTag("errordetails");
        for (Iterator i = defaultErrorHit.errorIterator(); i.hasNext();) {
            ErrorMessage error = (ErrorMessage) i.next();
            renderMessageDefaultErrorHit(writer, error);
        }
        writer.closeTag();
        return true;
    }

    public static void renderMessageDefaultErrorHit(XMLWriter writer, ErrorMessage error) throws IOException {
        writer.openTag("error");
        writer.attribute("source", error.getSource());
        writer.attribute("error", error.getMessage());
        writer.attribute("code", Integer.toString(error.getCode()));
        writer.content(error.getDetailedMessage(), false);
        if (error.getCause()!=null) {
            writer.openTag("cause");
            writer.content("\n", true);
            StringWriter stackTrace=new StringWriter();
            error.getCause().printStackTrace(new PrintWriter(stackTrace));
            writer.content(stackTrace.toString(), true);
            writer.closeTag();
        }
        writer.closeTag();
    }

    public static final class RenderingVisitor extends TraceVisitor {

        private static final String tag = "p";
        private final XMLWriter writer;
        private long baseTime;

        public RenderingVisitor(XMLWriter writer,long baseTime) {
            this.writer=writer;
            this.baseTime=baseTime;
        }

        @Override
        public void entering(TraceNode node) {
            if (node.isRoot()) return;
            writer.openTag(tag);
        }

        @Override
        public void leaving(TraceNode node) {
            if (node.isRoot()) return;
            writer.closeTag();
        }

        @Override
        public void visit(TraceNode node) {
            if (node.isRoot()) return;
            if (node.payload()==null) return;

            writer.openTag(tag);
            if (node.timestamp()!=0)
                writer.content(node.timestamp()-baseTime,false).content(" ms: ", false);
            writer.content(node.payload().toString(),false);
            writer.closeTag();
        }

    }

    private Result getResult() {
        Result r;
        try {
            r = (Result) getResponse();
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(
                    "DefaultRenderer attempted used outside a search context, got a "
                    + getResponse().getClass().getName());
        }
        return r;
    }

    @Override
    public void beginResponse(OutputStream stream) throws IOException {
        Charset cs = Charset.forName(getRequestedEncoding(getResult().getQuery()));
        CharsetEncoder encoder = cs.newEncoder();
        writer = wrapWriter(new ByteWriter(stream, encoder));

        header(writer, getResult());
        if (getResult().hits().getError() != null || getResult().hits().getQuery().errors().size() > 0) {
            error(writer, getResult());
        }

        if (getResult().getConcreteHitCount() == 0) {
            emptyResult(writer, getResult());
        }

        if (getResult().getContext(false) != null) {
            queryContext(writer, getResult().getContext(false), getResult().getQuery());
        }

    }

    /** Returns the encoding of the query, or the encoding given by the template if none is set */
    public final String getRequestedEncoding(Query query) {
        String encoding = query.getModel().getEncoding();
        if (encoding != null) return encoding;
        return getEncoding();
    }

    @Override
    public void beginList(DataList<?> list) throws IOException {
        if (getRecursionLevel() == 1) return;

        HitGroup hit = (HitGroup) list;
        boolean renderedSimple = simpleRenderHit(writer, hit);
        if (renderedSimple) return;

        renderHitGroup(writer, hit);
    }

    @Override
    public void data(Data data) throws IOException {
        Hit hit = (Hit) data;
        boolean renderedSimple = simpleRenderHit(writer, hit);
        if (renderedSimple) return;

        renderSingularHit(writer, hit);
        writer.closeTag();
    }

    @Override
    public void endList(DataList<?> list) {
        if (getRecursionLevel() > 1)
            writer.closeTag();
    }

    @Override
    public void endResponse() {
        writer.closeTag();
        writer.close();
    }

}
