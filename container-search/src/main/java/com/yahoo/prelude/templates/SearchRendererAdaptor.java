// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.templates;

import com.yahoo.prelude.fastsearch.GroupingListHit;
import com.yahoo.search.Result;
import com.yahoo.search.query.context.QueryContext;
import com.yahoo.search.rendering.Renderer;
import com.yahoo.search.result.*;
import com.yahoo.search.result.ErrorHit;
import com.yahoo.processing.request.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.text.XMLWriter;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * Renders a search result using the old templates API.
 *
 * @author tonytv
 * @deprecated do not use
 */
@SuppressWarnings({ "rawtypes", "deprecation", "unchecked" })
@Deprecated // TODO: Remove on Vespa 7
public final class SearchRendererAdaptor extends Renderer {

    private final LogExceptionUserTemplateDelegator templates;

    //Per instance members, must be created at rendering time, not construction time due to cloning.
    private Context context;

    public SearchRendererAdaptor(UserTemplate userTemplate) {
        templates = new LogExceptionUserTemplateDelegator(userTemplate);
    }

    @Override
    public void init() {
        super.init();
        context = templates.createContext();
    }

    /** A legacy test utility - do not use. */
    public static void callRender(OutputStream stream, Result result) throws IOException {
        Renderer rendererAdaptor = new SearchRendererAdaptor(result.getTemplating().getTemplates());
        rendererAdaptor.init();
        result.getTemplating().setRenderer(rendererAdaptor);
        rendererAdaptor.render(stream, result, result.getQuery().getModel().getExecution(), result.getQuery());
    }

    @Override
    public String getEncoding() {
        return templates.getEncoding();
    }

    @Override
    public String getMimeType() {
        return templates.getMimeType();
    }

    @Override
    public String getDefaultSummaryClass() {
        return templates.getSummaryClass();
    }

    /**
     * Renders this result
     */
    public void render(Writer writer, Result result) throws java.io.IOException {
        Writer wrappedWriter = wrapWriter(writer);

        beginResult(wrappedWriter, result);

        if (result.hits().getError() != null || result.hits().getQuery().errors().size() > 0) {
            error(wrappedWriter, Collections.unmodifiableCollection(
                                      all(result.hits().getQuery().errors(), result.hits().getError())));
        }

        if (result.getConcreteHitCount() == 0) {
            emptyResult(wrappedWriter, result);
        }

        if (result.getContext(false) != null) {
            queryContext(wrappedWriter, result.getContext(false));
        }

        renderHitGroup(wrappedWriter, result.hits(), result.hits().getQuery().getOffset() + 1);

        endResult(wrappedWriter, result);
    }


    private <T> Collection<T> all(Collection<T> collection, T extra) {
        Collection<T> result = new ArrayList<>(collection);
        result.add(extra);
        return result;
    }


    public Writer wrapWriter(Writer writer) {
        return templates.wrapWriter(writer);
    }


    public void beginResult(Writer writer, Result result) throws IOException {
        context.put("context", context);
        context.put("result", result);
        context.setBoldOpenTag(templates.getBoldOpenTag());
        context.setBoldCloseTag(templates.getBoldCloseTag());
        context.setSeparatorTag(templates.getSeparatorTag());

        templates.header(context, writer);
    }

    public void endResult(Writer writer, Result result) throws IOException {
        templates.footer(context, writer);
    }

    public void error(Writer writer, Collection<ErrorMessage> errorMessages) throws IOException {
        templates.error(context, writer);
    }


    public void emptyResult(Writer writer, Result result) throws IOException {
        templates.noHits(context, writer);
    }

    public void queryContext(Writer writer, QueryContext queryContext) throws IOException {
        templates.queryContext(context, writer);
    }

    private  void renderHitGroup(Writer writer, HitGroup hitGroup, int hitnumber)
            throws IOException {
        boolean defaultTemplate = templates.isDefaultTemplateSet();
        for (Hit hit : hitGroup.asList()) {
            if (!defaultTemplate && hit instanceof ErrorHit) continue; // TODO: Stop doing this

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
     * @param writer      the writer to append this hit to
     * @throws java.io.IOException if rendering fails
     */
    public void renderHit(Writer writer, Hit hit, int hitno) throws IOException {
        renderRegularHit(writer, hit, hitno);
    }

    private void renderRegularHit(Writer writer, Hit hit, int hitno) throws IOException {
        boolean renderedSimple = simpleRenderHit(writer, hit);

        if (renderedSimple) {
            return;
        }

        HitContext hitContext = new HitContext(hit, context);
        hitContext.put("hit", hit);
        hitContext.put("hitno", Integer.valueOf(hitno));
        hitContext.put("relevancy",hit.getRelevance());
        templates.hit(hitContext, writer);

        if (hit instanceof HitGroup)
            renderHitGroup(writer, (HitGroup) hit, hitno);

        // Put these back - may have been changed by nested rendering
        hitContext.put("hit", hit);
        hitContext.put("hitno", Integer.valueOf(hitno));
        templates.hitFooter(hitContext, writer);


        hitContext.remove("hit");
        hitContext.remove("hitno");
    }

    private boolean simpleRenderHit(Writer writer, Hit hit) throws IOException {
        if (hit instanceof DefaultErrorHit) {
            return simpleRenderDefaultErrorHit(writer, (DefaultErrorHit) hit);
        } else if (hit instanceof GroupingListHit) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean simpleRenderDefaultErrorHit(Writer writer, ErrorHit defaultErrorHit) throws IOException {
        XMLWriter xmlWriter=(writer instanceof XMLWriter) ? (XMLWriter)writer : new XMLWriter(writer,10,-1);
        xmlWriter.openTag("errordetails");
        for (Iterator i = defaultErrorHit.errorIterator(); i.hasNext();) {
            ErrorMessage error = (ErrorMessage) i.next();
            renderMessageDefaultErrorHit(xmlWriter, error);
        }
        xmlWriter.closeTag();
        return true;
    }

    public static void renderMessageDefaultErrorHit(XMLWriter writer, ErrorMessage error) throws IOException {
        writer.openTag("error");
        if (error instanceof com.yahoo.search.result.ErrorMessage)
            writer.attribute("source",((com.yahoo.search.result.ErrorMessage)error).getSource());
        writer.attribute("error",error.getMessage());
        writer.attribute("code",Integer.toString(error.getCode()));
        writer.content(error.getDetailedMessage(),false);
        if (error.getCause()!=null) {
            writer.openTag("cause");
            writer.content("\n",true);
            StringWriter stackTrace=new StringWriter();
            error.getCause().printStackTrace(new PrintWriter(stackTrace));
            writer.content(stackTrace.toString(),true);
            writer.closeTag();
        }
        writer.closeTag();
    }

    /**
     * Renders this hit as XML, disregarding the given template.
     * The main error will be rendered first, the all the following errors.
     */
    public boolean simpleRenderErrorHit(Writer writer, com.yahoo.search.result.ErrorHit errorHit) throws IOException {
        XMLWriter xmlWriter=(writer instanceof XMLWriter) ? (XMLWriter)writer : new XMLWriter(writer,10,-1);
        xmlWriter.openTag("errordetails");
        for (Iterator i = errorHit.errorIterator(); i.hasNext();) {
            ErrorMessage error = (ErrorMessage) i.next();
            rendererErrorHitMessageMessage(xmlWriter, errorHit, error);
        }
        xmlWriter.closeTag();
        return true;
    }

    public static void rendererErrorHitMessageMessage(XMLWriter writer, com.yahoo.search.result.ErrorHit errorHit, ErrorMessage error) throws IOException {
        writer.openTag("error");
        if (errorHit instanceof Hit) {
            writer.attribute("source", ((Hit) errorHit).getSource());
        }
        writer.attribute("error",error.getMessage());
        writer.attribute("code",Integer.toString(error.getCode()));
        writer.content(error.getDetailedMessage(),false);
        writer.closeTag();
    }

    /**
     * For internal use only
     */
    public UserTemplate getAdaptee() {
        return templates.getDelegate();
    }

}
