// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core;

import com.yahoo.vespa.http.client.FeedClient;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.ext.DefaultHandler2;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reads an input stream of xml, sends these to session.
 * @author dybis
*/
public class XmlFeedReader {

    // Static class.
    private XmlFeedReader() {}

    public static void read(InputStream inputStream, FeedClient feedClient, AtomicInteger numSent) throws Exception {

        SAXParserFactory parserFactor = SAXParserFactory.newInstance();
        parserFactor.setValidating(false);
        parserFactor.setNamespaceAware(false);
        final SAXParser parser = parserFactor.newSAXParser();
        SAXClientFeeder saxClientFeeder = new SAXClientFeeder(feedClient, numSent);
        SAXClientFeeder handler = saxClientFeeder;

        InputSource inputSource = new InputSource();
        inputSource.setEncoding(StandardCharsets.UTF_8.displayName());
        inputSource.setByteStream(inputStream);
        // This is to send events about CDATA to the saxClientFeeder 
        // (https://docs.oracle.com/javase/tutorial/jaxp/sax/events.html)
        parser.setProperty("http://xml.org/sax/properties/lexical-handler", saxClientFeeder);

        parser.parse(inputSource, handler);
    }
}

/**
 * Streams XML and sends each document operation to feeder.
 */
class SAXClientFeeder extends DefaultHandler2 {

    public static final String CDATA_START = "<![CDATA[";
    public static final String CDATA_STOP = "]]>";
    private final FeedClient feedClient;
    int vespaIndent = 0;
    int documentIndent = 0;
    String documentId = null;
    StringBuilder content = new StringBuilder();
    final AtomicInteger numSent;
    boolean isCData = false;

    public SAXClientFeeder(FeedClient feedClient, AtomicInteger numSent) {
        this.feedClient = feedClient;
        this.numSent = numSent;
    }

    @Override
    public void startCDATA() {
        content.append(CDATA_START);
        isCData = true;
    }

    @Override
    public void endCDATA() {
        content.append(CDATA_STOP);
        isCData = false;
    }

    @Override
    public void comment(char[] ch, int start, int length) { }

    @SuppressWarnings("fallthrough")
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        switch(qName){
            case "vespafeed":
                vespaIndent++;
                if (vespaIndent == 1 && documentIndent == 0) {
                    // If this is the first vespafeed tag, it should not be added to content of the first item.
                    return;
                }
            case "update":
            case "remove":
            case "document" :
                documentIndent++;
                documentId = attributes.getValue("documentid");
                content = new StringBuilder();
        }
        content.append("<" + qName);
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength (); i++) {
                content.append(" ")
                        .append(attributes.getQName(i))
                        .append("=\"");
                String attributesValue = attributes.getValue(i);
                characters(attributesValue.toCharArray(), 0, attributesValue.length());
                content.append("\"");
            }
        }
        content.append(">");
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        content.append("</")
                .append(qName)
                .append(">");
        switch(qName){
            case "vespafeed":
                vespaIndent--;
                return;
            case "update":
            case "remove":
            case "document" :
                documentIndent--;
                if (documentIndent == 0) {
                    if (documentId == null || documentId.isEmpty()) {
                        throw new IllegalArgumentException("no docid");
                    }
                    feedClient.stream(documentId, content);
                    numSent.incrementAndGet();
                }
        }
    }

    @Override
    public void characters (char buf [], int offset, int len) {
        if (isCData) {
            content.append(buf, offset, len);
            return;
        }

        // This is on the critical loop for performance, otherwise a library would have been used.
        // We can do a few shortcuts as well as this data is already decoded by SAX parser.
        for (int x = offset ; x < len + offset ; x++) {
            switch (buf[x]) {
                case '&' : content.append("&amp;"); continue;
                case '<' : content.append("&lt;"); continue;
                case '>' : content.append("&gt;"); continue;
                case '"' : content.append("&quot;"); continue;
                case '\'' : content.append("&apos;"); continue;
                default: content.append(buf[x]); continue;
            }
        }
    }

}
