// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.result;

import com.yahoo.io.ByteWriter;
import com.yahoo.prelude.fastsearch.GroupingListHit;
import com.yahoo.prelude.hitfield.HitField;
import com.yahoo.prelude.hitfield.JSONString;
import com.yahoo.prelude.hitfield.XMLString;
import com.yahoo.processing.rendering.AsynchronousSectionedRenderer;
import com.yahoo.processing.response.Data;
import com.yahoo.processing.response.DataList;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.pagetemplates.model.Renderer;
import com.yahoo.search.pagetemplates.model.Source;
import com.yahoo.search.query.context.QueryContext;
import com.yahoo.search.rendering.XmlRenderer;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.DefaultErrorHit;
import com.yahoo.search.result.ErrorHit;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.result.StructuredData;
import com.yahoo.text.Utf8String;
import com.yahoo.text.XML;
import com.yahoo.text.XMLWriter;
import com.yahoo.yolean.trace.TraceNode;

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

public class PageTemplatesXmlRenderer extends AsynchronousSectionedRenderer<Result> {

    public static final String  DEFAULT_MIMETYPE    = "text/xml";
    public static final String  DEFAULT_ENCODING    = "utf-8";

    private static final Utf8String GROUP = new Utf8String("group");
    private static final Utf8String HIT = new Utf8String("hit");
    private static final Utf8String ERROR = new Utf8String("error");
    private static final Utf8String CODE = new Utf8String("code");
    private static final Utf8String COVERAGE_DOCS = new Utf8String("coverage-docs");
    private static final Utf8String COVERAGE_NODES = new Utf8String("coverage-nodes");
    private static final Utf8String COVERAGE_FULL = new Utf8String("coverage-full");
    private static final Utf8String COVERAGE = new Utf8String("coverage");
    private static final Utf8String RESULTS_FULL = new Utf8String("results-full");
    private static final Utf8String RESULTS = new Utf8String("results");
    private static final Utf8String TYPE = new Utf8String("type");
    private static final Utf8String RELEVANCE = new Utf8String("relevance");
    private static final Utf8String SOURCE = new Utf8String("source");

    private XMLWriter writer;

    public PageTemplatesXmlRenderer() {
        this(null);
    }

    /**
     * Creates a json renderer using a custom executor.
     * Using a custom executor is useful for tests to avoid creating new threads for each renderer registry.
     */
    public PageTemplatesXmlRenderer(Executor executor) {
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

    private void header(XMLWriter writer, Result result) {
        writer.xmlHeader(getRequestedEncoding(result.getQuery()));
        writer.openTag("page").attribute("version", "1.0")
                              .attribute("layout", result.hits().getField("layout"));
        renderCoverageAttributes(result.getCoverage(false), writer);
        writer.closeStartTag();
        renderSectionContent(writer, result.hits());
    }

    private static void renderCoverageAttributes(Coverage coverage, XMLWriter writer) {
        if (coverage == null) return;
        writer.attribute(COVERAGE_DOCS,coverage.getDocs());
        writer.attribute(COVERAGE_NODES,coverage.getNodes());
        writer.attribute(COVERAGE_FULL,coverage.getFull());
        writer.attribute(COVERAGE,coverage.getResultPercentage());
        writer.attribute(RESULTS_FULL,coverage.getFullResultSets());
        writer.attribute(RESULTS,coverage.getResultSets());
    }

    public void error(XMLWriter writer, Result result) {
        ErrorMessage error = result.hits().getError();
        writer.openTag(ERROR).attribute(CODE,error.getCode()).content(error.getMessage(),false).closeTag();
    }

    private void queryContext(XMLWriter writer, Query owner) {
        if (owner.getTrace().getLevel()!=0) {
            XMLWriter xmlWriter=XMLWriter.from(writer);
            xmlWriter.openTag("meta").attribute("type", QueryContext.ID);
            TraceNode traceRoot = owner.getModel().getExecution().trace().traceNode().root();
            traceRoot.accept(new XmlRenderer.RenderingVisitor(xmlWriter, owner.getStartTime()));
            xmlWriter.closeTag();
        }
    }

    private void renderSingularHit(XMLWriter writer, Hit hit) {
        if ( ! hit.isMeta() &&  ! writer.isIn("content"))
            writer.openTag("content");

        writer.openTag(HIT);
        renderHitAttributes(hit,writer);
        writer.closeStartTag();
        renderField(writer, "id", hit.getId());
        hit.forEachField((name, value) -> renderField(writer, name, value));
        writer.closeTag();
    }

    /** Writes a hit's default attributes like 'type', 'source', 'relevance'. */
    private void renderHitAttributes(Hit hit, XMLWriter writer) {
        writer.attribute(TYPE, hit.types().stream().collect(Collectors.joining(" ")));
        if (hit.getRelevance() != null)
            writer.attribute(RELEVANCE, hit.getRelevance().toString());
        writer.attribute(SOURCE, hit.getSource());
    }

    private void renderField(XMLWriter writer, String name, Object value) {
        writer.openTag(name);
        renderFieldContent(writer, value);
        writer.closeTag();
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

    private void renderHitAttributes(XMLWriter writer, Hit hit) {
        writer.attribute(TYPE, hit.types().stream().collect(Collectors.joining(" ")));
        if (hit.getRelevance() != null)
            writer.attribute(RELEVANCE, hit.getRelevance().toString());
        writer.attribute(SOURCE, hit.getSource());
    }

    private void renderHitGroup(XMLWriter writer, HitGroup hit) {
        if (hit.types().contains("section")) {
            renderSection(writer, hit); // Renders /result/section
        }
        else if (hit.types().contains("meta")) {
            writer.openTag("meta"); // renders /result/meta
            writer.closeStartTag();
        }
        else {
            renderGroup(writer, hit);
        }
    }

    private void renderGroup(XMLWriter writer, HitGroup hit) {
        writer.openTag(GROUP);
        renderHitAttributes(writer, hit);
        writer.closeStartTag();
    }

    private void renderSection(XMLWriter writer, HitGroup hit) {
        writer.openTag("section");
        writer.attribute("id", hit.getDisplayId());
        writer.attribute("layout", hit.getField("layout"));
        writer.attribute("region", hit.getField("region"));
        writer.attribute("placement", hit.getField("placement")); // deprecated in 5.0
        writer.closeStartTag();
        renderSectionContent(writer, hit);
    }

    private void renderSectionContent(XMLWriter writer, HitGroup hit) {
        if (hit instanceof SectionHitGroup) { // render additional information
            SectionHitGroup sectionGroup = (SectionHitGroup)hit;
            for (Source source : sectionGroup.sources()) {
                writer.openTag("source").attribute("url", source.getUrl());
                renderParameters(source.parameters(), writer);
                writer.closeTag();
            }
            for (Renderer renderer : sectionGroup.renderers()) {
                writer.openTag("renderer").attribute("for", renderer.getRendererFor()).attribute("name", renderer.getName());
                renderParameters(renderer.parameters(), writer);
                writer.closeTag();
            }
        }
    }

    private void renderParameters(Map<String,String> parameters, XMLWriter writer) {
        // Render content
        for (Map.Entry<String, String> parameter : parameters.entrySet())
            writer.openTag("parameter").attribute("name", parameter.getKey())
                                       .content(parameter.getValue(), false)
                  .closeTag();
    }

    private boolean simpleRenderHit(XMLWriter writer, Hit hit) {
        if (hit instanceof DefaultErrorHit) {
            return simpleRenderDefaultErrorHit(writer, (DefaultErrorHit) hit);
        } else if (hit instanceof GroupingListHit) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean simpleRenderDefaultErrorHit(XMLWriter writer, ErrorHit defaultErrorHit) {
        writer.openTag("errordetails");
        for (Iterator i = defaultErrorHit.errorIterator(); i.hasNext();) {
            ErrorMessage error = (ErrorMessage) i.next();
            renderMessageDefaultErrorHit(writer, error);
        }
        writer.closeTag();
        return true;
    }

    public static void renderMessageDefaultErrorHit(XMLWriter writer, ErrorMessage error) {
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

    private Result getResult() {
        try {
            return (Result)getResponse();
        } catch (ClassCastException e) {
            throw new IllegalStateException("PageTemplatesXmlRenderer attempted used outside a search context, got a " +
                                            getResponse().getClass().getName());
        }
    }

    @Override
    public void beginResponse(OutputStream stream) {
        Charset cs = Charset.forName(getRequestedEncoding(getResult().getQuery()));
        CharsetEncoder encoder = cs.newEncoder();
        writer = wrapWriter(new ByteWriter(stream, encoder));

        header(writer, getResult());
        if (getResult().hits().getError() != null || getResult().hits().getQuery().errors().size() > 0)
            error(writer, getResult());

        if (getResult().getContext(false) != null)
            queryContext(writer, getResult().getQuery());
    }

    /** Returns the encoding of the query, or the encoding given by the template if none is set */
    public final String getRequestedEncoding(Query query) {
        String encoding = query.getModel().getEncoding();
        if (encoding != null) return encoding;
        return getEncoding();
    }

    @Override
    public void beginList(DataList<?> list) {
        if (getRecursionLevel() == 1) return;

        HitGroup hit = (HitGroup) list;
        boolean renderedSimple = simpleRenderHit(writer, hit);
        if (renderedSimple) return;

        renderHitGroup(writer, hit);
    }

    @Override
    public void data(Data data) {
        Hit hit = (Hit) data;
        boolean renderedSimple = simpleRenderHit(writer, hit);
        if ( ! renderedSimple)
            renderSingularHit(writer, hit);
    }

    @Override
    public void endList(DataList<?> list) {
        if (writer.isIn("content"))
            writer.closeTag();
        if (getRecursionLevel() > 1)
            writer.closeTag();
    }

    @Override
    public void endResponse() {
        writer.closeTag();
        writer.close();
    }

}
