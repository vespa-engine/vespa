// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.rendering;

import com.yahoo.concurrent.CopyOnWriteHashMap;
import com.yahoo.io.ByteWriter;
import com.yahoo.log.LogLevel;
import com.yahoo.net.URI;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.GroupingListHit;
import com.yahoo.prelude.hitfield.HitField;
import com.yahoo.prelude.hitfield.JSONString;
import com.yahoo.prelude.hitfield.XMLString;
import com.yahoo.prelude.templates.Context;
import com.yahoo.prelude.templates.DefaultTemplateSet;
import com.yahoo.prelude.templates.MapContext;
import com.yahoo.prelude.templates.UserTemplate;
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author tonytv
 */
@SuppressWarnings({ "rawtypes", "deprecation" })
public final class SyncDefaultRenderer extends Renderer {

    private static final Logger log = Logger.getLogger(SyncDefaultRenderer.class.getName());

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


    //Per instance members, must be created at rendering time, not construction time due to cloning.
    private Context context;

    private final DefaultTemplateSet defaultTemplate = new DefaultTemplateSet();

    private final CopyOnWriteHashMap<String, Utf8String> fieldNameMap = new CopyOnWriteHashMap<>();

    @Override
    public void init() {
        super.init();
        context = new MapContext();
    }

    @Override
    public String getEncoding() {
        return DEFAULT_ENCODING;
    }

    @Override
    public String getMimeType() {
        return DEFAULT_MIMETYPE;
    }

    @Override
    public String getDefaultSummaryClass() {
        return null;
    }

    private XMLWriter wrapWriter(Writer writer) {
        return XMLWriter.from(writer, 10, -1);
    }

    /**
     * Renders this result
     */
    public void render(Writer writer, Result result) throws IOException {
        XMLWriter xmlWriter = wrapWriter(writer);

        context.put("context", context);
        context.put("result", result);
        context.setBoldOpenTag(defaultTemplate.getBoldOpenTag());
        context.setBoldCloseTag(defaultTemplate.getBoldCloseTag());
        context.setSeparatorTag(defaultTemplate.getSeparatorTag());

        try {
            header(xmlWriter, result);
        } catch (Exception e) {
            handleException(e);
        }

        if (result.hits().getError() != null || result.hits().getQuery().errors().size() > 0) {
            error(xmlWriter, result);
        }

        if (result.getConcreteHitCount() == 0) {
            emptyResult(xmlWriter, result);
        }

        if (result.getContext(false) != null) {
            queryContext(xmlWriter, result.getContext(false), result.getQuery());
        }

        renderHitGroup(xmlWriter, result.hits(), result.hits().getQuery().getOffset() + 1);

        endResult(xmlWriter, result);
    }

    private void header(XMLWriter writer, Result result) throws IOException {
        // TODO: move setting this to Result
        context.setUtf8Output("utf-8".equalsIgnoreCase(getRequestedEncoding(result.getQuery())));
        writer.xmlHeader(getRequestedEncoding(result.getQuery()));
        writer.openTag(RESULT).attribute(TOTAL_HIT_COUNT,String.valueOf(result.getTotalHitCount()));
        renderCoverageAttributes(result.getCoverage(false), writer);
        renderTime(writer, result);
        writer.closeStartTag();
    }

    private void renderTime(XMLWriter writer, Result result) {
        if (!result.getQuery().getPresentation().getTiming()) {
            return;
        }

        final String threeDecimals = "%.3f";
        final double milli = .001d;
        final long now = System.currentTimeMillis();
        final long searchTime = now - result.getQuery().getStartTime();
        final double searchSeconds = ((double) searchTime) * milli;

        if (result.getElapsedTime().firstFill() != 0L) {
            final long queryTime = result.getElapsedTime().firstFill() - result.getQuery().getStartTime();
            final long summaryFetchTime = now - result.getElapsedTime().firstFill();
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

    public void endResult(XMLWriter writer, Result result) throws IOException {
        try {
            writer.closeTag();
        } catch (Exception e) {
            handleException(e);
        }
    }

    public void error(XMLWriter writer, Result result) throws IOException {
        try {
            ErrorMessage error = result.hits().getError();
            writer.openTag(ERROR).attribute(CODE,error.getCode()).content(error.getMessage(),false).closeTag();
        } catch (Exception e) {
            handleException(e);
        }
    }


    protected void emptyResult(XMLWriter writer, Result result) throws IOException {}

    public void queryContext(XMLWriter writer, QueryContext queryContext, Query owner) throws IOException {
        try {
            if (owner.getTraceLevel()!=0) {
                XMLWriter xmlWriter=XMLWriter.from(writer);
                xmlWriter.openTag("meta").attribute("type", QueryContext.ID);
                TraceNode traceRoot = owner.getModel().getExecution().trace().traceNode().root();
                traceRoot.accept(new RenderingVisitor(xmlWriter, owner.getStartTime()));
                xmlWriter.closeTag();
            }
        } catch (Exception e) {
            handleException(e);
        }
    }

    private  void renderHitGroup(XMLWriter writer, HitGroup hitGroup, int hitnumber)
            throws IOException {
        for (Hit hit : hitGroup.asList()) {
            renderHit(writer, hit, hitnumber);
            if (!hit.isAuxiliary())
                hitnumber++;
        }
    }


    /**
     * Renders this hit as xml. The default implementation will call the simpleRender()
     * hook. If it returns true, nothing more is done, otherwise the
     * given template set will be used for rendering.
     *
     *
     * @param writer      the XMLWriter to append this hit to
     * @throws java.io.IOException if rendering fails
     */
    public void renderHit(XMLWriter writer, Hit hit, int hitno) throws IOException {
        renderRegularHit(writer, hit, hitno);
    }

    private void renderRegularHit(XMLWriter writer, Hit hit, int hitno) throws IOException {
        boolean renderedSimple = simpleRenderHit(writer, hit);

        if (renderedSimple) {
            return;
        }

        try {
            if (hit instanceof HitGroup) {
                renderHitGroup(writer, (HitGroup) hit);
            } else {
                renderSingularHit(writer, hit);
            }
        } catch (Exception e) {
            handleException(e);
        }

        if (hit instanceof HitGroup)
            renderHitGroup(writer, (HitGroup) hit, hitno);

        try {
            writer.closeTag();
        } catch (Exception e) {
            handleException(e);
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
        hit.forEachField((name, value) -> renderField(writer, name, value));
    }

    private void renderField(XMLWriter writer, String name, Object value) {
        if (name.startsWith("$")) return;

        writeOpenFieldElement(writer, name);
        renderFieldContent(writer, value);
        writeCloseFieldElement(writer);
    }

    private void renderFieldContent(XMLWriter writer, Object value) {
        writer.escapedContent(asXML(value), false);
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
        if (hit.getRelevance() != null) {
            writer.attribute(RELEVANCY, hit.getRelevance().toString());
}
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

    private void handleException(Exception e) throws IOException {
        if (e instanceof IOException) {
            throw (IOException) e;
        } else {
            log.log(LogLevel.WARNING, "Exception thrown when rendering the result:", e);
        }
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
}
