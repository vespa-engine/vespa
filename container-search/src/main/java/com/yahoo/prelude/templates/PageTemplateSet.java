// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.templates;

import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.text.XMLWriter;

import java.io.IOException;
import java.io.Writer;

/**
 * A template implementing the 'page' format.
 * This is a variant of the tiled template set - see that class for details.
 *
 * @author bratseth
 * @deprecated use a Renderer instead
 */
@SuppressWarnings("deprecation")
@Deprecated // TODO: Remove on Vespa 7
public class PageTemplateSet extends TiledTemplateSet {

    public PageTemplateSet() {
        super("page");
    }

    @Override
    /** Uses an XML writer in this */
    public XMLWriter wrapWriter(Writer writer) { return new XMLWriter(super.wrapWriter(writer)); }

    @Override
    public void header(Context context,XMLWriter writer) throws IOException {
        Result result=(Result)context.get("result");
        writer.xmlHeader(getRequestedEncoding(result.getQuery()));
        writer.openTag("page").attribute("version","1.0").attribute("layout",result.hits().getField("layout"));
        renderCoverageAttributes(result.getCoverage(false), writer);
        writer.closeStartTag();
        renderSectionContent(result.hits(),writer);
    }

    @Override
    public void footer(Context context,XMLWriter writer) throws IOException {
        if (writer.isIn("content"))
            writer.closeTag();
        super.footer(context,writer);
    }

    @Override
    protected void renderSection(HitGroup hit, XMLWriter writer) throws IOException {
        writer.openTag("section");
        writer.attribute("id",hit.getDisplayId());
        writer.attribute("layout",hit.getField("layout"));
        writer.attribute("region",hit.getField("region"));
        writer.closeStartTag();
        renderSectionContent(hit,writer);
    }

    @Override
    public void hit(Context context, XMLWriter writer) throws IOException {
        Hit hit = (Hit) context.get("hit");
        if (!hit.isMeta() && !writer.isIn("content"))
            writer.openTag("content");
        super.hit(context,writer);
    }

    @Override
    public void hitFooter(Context context, XMLWriter writer) throws IOException {
        if (writer.isIn("content"))
            writer.closeTag();
        super.hitFooter(context, writer);
    }

    public String toString() { return "page template"; }

}
