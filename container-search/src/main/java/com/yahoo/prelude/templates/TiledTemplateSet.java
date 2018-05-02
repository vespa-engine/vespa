// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.templates;

import com.yahoo.container.ConfigHack;
import com.yahoo.prelude.hitfield.HitField;
import com.yahoo.prelude.hitfield.JSONString;
import com.yahoo.prelude.hitfield.XMLString;
import com.yahoo.prelude.templates.FormattingOptions.SubtypeFieldWithPrefix;
import com.yahoo.search.Result;
import com.yahoo.search.pagetemplates.model.Renderer;
import com.yahoo.search.pagetemplates.model.Source;
import com.yahoo.search.pagetemplates.result.SectionHitGroup;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.result.StructuredData;
import com.yahoo.text.XML;
import com.yahoo.text.XMLWriter;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A template set which implements the 'tiled' format.
 *
 * This template implementation requires a few rules to be observed for it to work properly:
 * <ul>
 * <li>As hit fields are rendered as XML tag names, their name must be compatible with XML tag names.</li>
 * <li>Results sections, meta section, provider tags are rendered based on hits having specific types (as in {@link Hit#types()},
 * see table below for a list of hit types that are needed in order for hits to render properly.</li>
 * <li>Some fields inside hits corresponding to provider tags (/result/meta/provider) are formatted in a specific way, see provider fields formatting options
 * below. Other fields are rendered the usual way.</li>
 * </ul>
 *
 * <p>Hit types required for proper rendering</p>
 * <table summary="Hit types required for proper rendering">
 * <tr><td>XML tag path</td><td>Required hit type</td></tr>
 * <tr><td>/result/section</td><td>A hit group and have a "section" type</td></tr>
 * <tr><td>/result/meta</td><td>A hit group and have a "meta" type</td></tr>
 * <tr><td>/result/meta/provider</td><td>A hit that has a "logging" type</td></tr>
 * </table>
 *
 * <p>Provider fields formatting options</p>
 * <table summary="Provider fields formatting options">
 * <tr><td>Field</td><td>Formatting</td><td>Field type</td></tr>
 * <tr><td>provider</td><td>name attribute of &lt;provider&gt; tag</td><td>Provided by container</td></tr>
 * <tr><td>scheme</td><td>scheme attribute of &lt;provider&gt; tag</td><td>Provided by container</td></tr>
 * <tr><td>host</td><td>host attribute of &lt;provider&gt; tag</td><td>Provided by container</td></tr>
 * <tr><td>port</td><td>port attribute of &lt;provider&gt; tag</td><td>Provided by container</td></tr>
 * <tr><td>path</td><td>path attribute of &lt;provider&gt; tag</td><td>Provided by container</td></tr>
 * <tr><td>status</td><td>result attribute of &lt;provider&gt; tag</td><td>Provided by container</td></tr>
 * <tr><td>latency_connect</td><td>&lt;latency type="connect"&gt; tag</td><td>Provided by container</td></tr>
 * <tr><td>latency_start</td><td>&lt;latency type="start"&gt; tag</td><td>Provided by container</td></tr>
 * <tr><td>latency_finish</td><td>&lt;latency type="finish"&gt; tag</td><td>Provided by container</td></tr>
 * <tr><td>query_param_*</td><td>&lt;parameter name="..."&gt; tag</td><td>Provided by container</td></tr>
 * <tr><td>header_*</td><td>&lt;header name="..."&gt; tag</td><td>Provided by container</td></tr>
 * <tr><td>response_header_*</td><td>&lt;response-header name="..."&gt; tag</td><td>Provided by container</td></tr>
 * <tr><td>count_first</td><td>&lt;count type="first"&gt; tag</td><td>Provided by container</td></tr>
 * <tr><td>count_last</td><td>&lt;count type="last"&gt; tag</td><td>Provided by container</td></tr>
 * <tr><td>count_total</td><td>&lt;count type="total"&gt; tag</td><td>Provided by container</td></tr>
 * <tr><td>count_deep</td><td>&lt;count type="deep"&gt; tag</td><td>Provided by container</td></tr>
 * <tr><td>queryattrs_xorronum</td><td>&lt;queryattrs name="xorronum"&gt; tag</td><td>Provided by YST searcher</td></tr>
 * <tr><td>queryattrs_RankFeaturesRewriterAttr</td><td>&lt;queryattrs name="RankFeaturesRewriterAttr"&gt; tag</td><td>Provided by YST searcher</td></tr>
 * <tr><td>queryattrs_intlannotator</td><td>&lt;queryattrs name="intlannotator"&gt; tag</td><td>Provided by YST searcher</td></tr>
 * <tr><td>queryattrs_category</td><td>&lt;queryattrs name="category"&gt; tag</td><td>Provided by YST searcher</td></tr>
 * <tr><td>wordcounts_*</td><td>&lt;wordcounts word="..."&gt; tag</td><td>Provided by YST searcher</td></tr>
 * </table>
 *
 * @author bratseth
 * @author laboisse
 */
public class TiledTemplateSet extends DefaultTemplateSet {

    private FormattingOptions hitOptionsForProvider;
    private FormattingOptions hitOptions;

    public TiledTemplateSet() {
        this(ConfigHack.TILED_TEMPLATE);
    }

    public TiledTemplateSet(String templateName) {
        super(templateName);

        // Define formatting options that will be used by various rendering methods
        hitOptions = new FormattingOptions();
        // Render provider field as an attribute, not as a regular field
        hitOptions.formatFieldAsAttribute("provider", "provider");
        hitOptions.setFieldNotToRender("provider");


        // Define formatting options that will be used by various rendering methods, for /result/meta/provider tags
        hitOptionsForProvider = new FormattingOptions();
        hitOptionsForProvider.formatFieldAsAttribute("provider", "name"); // Provider name is rendered a provider/@name
        // hitOptionsForProvider.formatFieldAsAttribute("uri", "query"); // FIXME Issue with attribute formatting, keeping as regular field for now
        hitOptionsForProvider.formatFieldAsAttribute("scheme", "scheme");
        hitOptionsForProvider.formatFieldAsAttribute("host", "host");
        hitOptionsForProvider.formatFieldAsAttribute("port", "port");
        hitOptionsForProvider.formatFieldAsAttribute("path", "path");
        hitOptionsForProvider.formatFieldAsAttribute("status", "result");
        // Latency fields are not defined using prefixes as we know all the field names and prefixes are expensive
        hitOptionsForProvider.formatFieldWithSubtype("latency_connect", "latency", "type", "connect");
        hitOptionsForProvider.formatFieldWithSubtype("latency_start", "latency", "type", "start");
        hitOptionsForProvider.formatFieldWithSubtype("latency_finish", "latency", "type", "finish");
        // Must use prefix for query parameters
        hitOptionsForProvider.formatFieldWithSubtype("query_param_", "parameter", "name");
        // Must use prefix for getHeaders
        hitOptionsForProvider.formatFieldWithSubtype("header_", "header", "name");
        // Must use prefix for response getHeaders
        hitOptionsForProvider.formatFieldWithSubtype("response_header_", "response-header", "name");
        // Count fields are not defined using prefixes as we know all the field names and prefixes are expensive
        hitOptionsForProvider.formatFieldWithSubtype("count_first", "count", "type", "first");
        hitOptionsForProvider.formatFieldWithSubtype("count_last", "count", "type", "last");
        hitOptionsForProvider.formatFieldWithSubtype("count_total", "count", "type", "total");
        hitOptionsForProvider.formatFieldWithSubtype("count_deep", "count", "type", "deep");

        hitOptionsForProvider.formatFieldWithSubtype("queryattrs_xorronum", "queryattrs", "name", "xorronum");
        hitOptionsForProvider.formatFieldWithSubtype("queryattrs_RankFeaturesRewriterAttr", "queryattrs", "name", "RankFeaturesRewriterAttr");
        hitOptionsForProvider.formatFieldWithSubtype("queryattrs_intlannotator", "queryattrs", "name", "intlannotator");
        hitOptionsForProvider.formatFieldWithSubtype("queryattrs_category", "queryattrs", "name", "category");

        hitOptionsForProvider.formatFieldWithSubtype("wordcounts_", "wordcounts", "word");
        // Provider field should not be rendered in logging hits as we already have <provider name="...">
        hitOptionsForProvider.setFieldNotToRender("provider");
    }

    @Override
    /** Uses an XML writer in this template */
    public XMLWriter wrapWriter(Writer writer) { return new XMLWriter(super.wrapWriter(writer)); }

    @Override
    public void header(Context context,XMLWriter writer) throws IOException {
        Result result=(Result)context.get("result");
        writer.xmlHeader(getRequestedEncoding(result.getQuery()));
        writer.openTag("result").attribute("version","1.0");
        writer.attribute("layout", result.hits().getField("layout"));
        renderCoverageAttributes(result.getCoverage(false), writer);
        writer.closeStartTag();
        renderSectionContent(result.hits(),writer);
    }

    /**
     * Augments default hit attributes rendering with formatting options.
     * There's also a hacky part: if hit is actually a hit group, tries to use
     * the 'type' field in place of the hit's type, to avoid having the 'group' hit type.
     */
    @Override
    protected void renderHitAttributes(Hit hit, XMLWriter writer) throws IOException {
    	if (hit instanceof HitGroup) {
    		String type = hit.types().stream().collect(Collectors.joining(" "));
    		if ("group".equals(type))
    			type = String.valueOf(hit.getField("type"));
    		writer.attribute("type", type);
    	}
        else {
    		writer.attribute("type", hit.types().stream().collect(Collectors.joining(" ")));
    	}

    	if (hit.getRelevance() != null)
            writer.attribute("relevance", hit.getRelevance());
        writer.attribute("source", hit.getSource());

        for (Map.Entry<String, String> attr : hitOptions.fieldsAsAttributes()) {
            Object val = hit.getField(attr.getKey());
            if (val != null)
                writer.attribute(attr.getValue(), String.valueOf(val));
        }
    }

    @Override
    protected void renderField(Context context, Hit hit, Map.Entry<String, Object> entry, XMLWriter writer) throws IOException {
        String fieldName = entry.getKey();

        if ( !shouldRenderField(hit, fieldName)) return;

        writer.openTag(fieldName);
        renderFieldContent(context, hit, fieldName, writer);
        writer.closeTag();
    }

    /** Renders all fields of the hit */
    @Override
    protected void renderHitFields(Context context, Hit hit, XMLWriter writer) throws IOException {
        renderId(hit.getId(), writer);
        for (Iterator<Map.Entry<String, Object>> it = hit.fieldIterator(); it.hasNext(); ) {
            Map.Entry<String, Object> entry = it.next();
            // Exclude fields that should not be rendered
            if (hitOptions.shouldRenderField(entry.getKey()))
                renderField(context, hit, entry, writer);
        }
    }

    @Override
    protected boolean shouldRenderField(Hit hit, String fieldName) {
        if (fieldName.equals("relevancy")) return false;
        if (fieldName.equals("collapseId")) return false;
        return true;
    }

    /**
     * Overrides {@link DefaultTemplateSet#hit(Context, Writer)}
     * to print 'logging' type meta hits as /result/meta/provider tags.
     * Fails back to {@code super.hit(context, writer)} in other cases.
     */
    @Override
    public void hit(Context context, XMLWriter writer) throws IOException {
        Hit hit = (Hit) context.get("hit");
        if (hit.isMeta() && hit.types().contains("logging"))
            renderProvider(context, hit, writer);
        else
            super.hit(context, writer);
    }

    /**
     * Overrides {@link DefaultTemplateSet#renderHitGroup(HitGroup, Context, XMLWriter)}
     * for /result/section and /result/meta hit groups.
     * Fails back to {@code super.renderHitGroup(hit, context, writer)} otherwise.
     */
    @Override
    protected void renderHitGroup(HitGroup hit, Context context, XMLWriter writer) throws IOException {
        if (hit.types().contains("section")) {
            renderSection(hit, writer); // Renders /result/section
        }
        else if (hit.types().contains("meta")) {
            writer.openTag("meta"); // renders /result/meta
            writer.closeStartTag();
        }
        else {
            super.renderHitGroup(hit, context, writer);
        }
	}

    /**
     * Renders /result/section.
     * Doesn't use {@link #renderHitAttributes(Hit, XMLWriter)}.
     */
    protected void renderSection(HitGroup hit, XMLWriter writer) throws IOException {
        writer.openTag("section");
        writer.attribute("id",hit.getDisplayId());
        writer.attribute("layout",hit.getField("layout"));
        writer.attribute("region",hit.getField("region"));
        writer.attribute("placement",hit.getField("placement")); // deprecated in 5.0
        writer.closeStartTag();
        renderSectionContent(hit,writer);
    }

    protected void renderSectionContent(HitGroup hit,XMLWriter writer) throws IOException {
        if (hit instanceof SectionHitGroup) { // render additional information
            SectionHitGroup sectionGroup=(SectionHitGroup)hit;
            for (Source source : sectionGroup.sources()) {
                writer.openTag("source").attribute("url",source.getUrl());
                renderParameters(source.parameters(),writer);
                writer.closeTag();
            }
            for (Renderer renderer : sectionGroup.renderers()) {
                writer.openTag("renderer").attribute("for",renderer.getRendererFor()).attribute("name",renderer.getName());
                renderParameters(renderer.parameters(),writer);
                writer.closeTag();
            }
        }
	}

    private void renderParameters(Map<String,String> parameters,XMLWriter writer) throws IOException {
        // Render content
        for (Map.Entry<String, String> parameter : parameters.entrySet())
            writer.openTag("parameter").attribute("name",parameter.getKey()).content(parameter.getValue(),false).closeTag();
    }

    /**
     * Renders /result/meta/provider.
     * Uses {@link #renderProviderHitAttributes(Hit, XMLWriter)} instead of the default {@link #renderHitAttributes(Hit, XMLWriter)}.
     * @see #renderProviderHitAttributes(Hit, XMLWriter)
     * @see #renderProviderHitFields(Context, Hit, XMLWriter)
     */
    protected void renderProvider(Context context, Hit hit, XMLWriter writer)
            throws IOException {
        writer.openTag("provider");
        renderProviderHitAttributes(hit, writer);
        writer.closeStartTag();
        renderProviderHitFields(context, hit, writer);
    }

    /**
     * Specific hit attributes rendering for 'provider' meta hits under /result/meta.
     */
    protected void renderProviderHitAttributes(Hit hit, XMLWriter writer) throws IOException {
        // Browse through fields that should be rendered as attributes
        for (Map.Entry<String, String> attr : hitOptionsForProvider.fieldsAsAttributes())
            writer.attribute(attr.getValue(),hit.getField(attr.getKey()));
    }


    /**
     * Renders fields under /result/meta/provider.
     *
     * @see #renderProviderField(Context, Hit, java.util.Map.Entry, XMLWriter)
     */
    protected void renderProviderHitFields(Context context, Hit hit, XMLWriter writer)
            throws IOException {
        renderId(hit.getId(), writer);
        for (Iterator<Map.Entry<String, Object>> it = hit.fieldIterator(); it.hasNext(); ) {
            Map.Entry<String, Object> entry = it.next();
            // Exclude fields that have already been rendered as attributes and
            // fields that should not be rendered
            if (hitOptionsForProvider.getAttributeName(entry.getKey()) == null
                    && hitOptionsForProvider.shouldRenderField(entry.getKey()))
                renderProviderField(context, hit, entry, writer);
        }
    }

    /**
     * Renders one field under /result/meta/provider.
     */
    protected void renderProviderField(Context context, Hit hit,
            Map.Entry<String, Object> entry, XMLWriter writer) throws IOException {

        String name = entry.getKey();
        FormattingOptions.SubtypeField subtypeField = hitOptionsForProvider.getSubtype(name);
        if (subtypeField == null)
            subtypeField = hitOptionsForProvider.getSubtypeWithPrefix(name);

        if (subtypeField != null) {
            writer.openTag(subtypeField.tagName);
            if (subtypeField.attributeValue != null) {
                writer.attribute(subtypeField.attributeName,subtypeField.attributeValue);
            }
            else if (subtypeField instanceof SubtypeFieldWithPrefix) {
                // This is a subtype field that was defined using a prefix
                // get the remaining part of the field name
                writer.attribute(subtypeField.attributeName,
                                 name.substring(((SubtypeFieldWithPrefix)subtypeField).prefixLength));
            }
        } else {
            writer.openTag(name);
        }
        writer.escapedContent(asXML(hit.getField(name)),false).closeTag();
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

    public String toString() { return "tiled result template"; }

}
