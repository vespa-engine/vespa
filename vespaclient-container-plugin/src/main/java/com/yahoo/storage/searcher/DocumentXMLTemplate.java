// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.storage.searcher;

import com.yahoo.log.LogLevel;
import com.yahoo.search.Result;
import com.yahoo.search.result.ErrorHit;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.result.Hit;
import com.yahoo.text.XML;

import java.io.IOException;
import java.io.Writer;
import java.util.logging.Logger;

/**
 * @deprecated use a renderer instead
 */
@Deprecated // TODO: Remove on Vespa 7
@SuppressWarnings("deprecation")
public class DocumentXMLTemplate extends com.yahoo.prelude.templates.UserTemplate<Writer> {

    private static final Logger log = Logger.getLogger(DocumentXMLTemplate.class.getName());

    public DocumentXMLTemplate() {
        super("vespa_xml");
    }

    public DocumentXMLTemplate(String mimeType, String encoding) {
        super("vespa_xml", mimeType, encoding);
    }

    private void writeErrorMessage(Writer writer, String type, int code,
                                   String message, String detailedMessage) throws IOException {
        writer.write("<error type=\"" + type + "\" code=\"" + code + "\" message=\"");
        writer.write(XML.xmlEscape(message, true));
        if (detailedMessage != null) {
            writer.write(": ");
            writer.write(XML.xmlEscape(detailedMessage, true));
        }
        writer.write("\"/>\n");
    }

    private void writeGenericErrorMessage(Writer writer, ErrorMessage message) throws IOException {
        // A bit dirty, but we don't have to support many different types
        if (message instanceof MessageBusErrorMessage) {
            writeErrorMessage(writer, "messagebus",
                    ((MessageBusErrorMessage)message).getMessageBusCode(),
                    message.getMessage(), message.getDetailedMessage());
        } else {
            writeErrorMessage(writer, "searcher", message.getCode(),
                    message.getMessage(), message.getDetailedMessage());
        }
    }

    @Override
    public void error(com.yahoo.prelude.templates.Context context, Writer writer) throws IOException {
        writer.write("<errors>\n");
        // If the error contains no error hits, use a single error with the main
        // code and description. Otherwise, use the error hits explicitly
        ErrorHit errorHit = ((Result)context.get("result")).hits().getErrorHit();
        if (errorHit == null || errorHit.errors().isEmpty()) {
            ErrorMessage message = ((Result)context.get("result")).hits().getError();
            writeGenericErrorMessage(writer, message);
        } else {
            for (ErrorMessage message : errorHit.errors()) {
                writeGenericErrorMessage(writer, message);
            }
        }
        writer.write("</errors>\n");
    }

    @Override
    public void header(com.yahoo.prelude.templates.Context context, Writer writer) throws IOException {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write("<result>\n");
        HitGroup rootGroup = ((Result) context.get("result")).hits();
        if (rootGroup.getField(VisitSearcher.VISITOR_CONTINUATION_TOKEN_FIELDNAME) != null) {
            writer.write("<continuation>" + rootGroup.getField(VisitSearcher.VISITOR_CONTINUATION_TOKEN_FIELDNAME) + "</continuation>");
        }
    }

    @Override
    public void footer(com.yahoo.prelude.templates.Context context, Writer writer) throws IOException {
        writer.write("</result>\n");
    }

    @Override
    public void hit(com.yahoo.prelude.templates.Context context, Writer writer) throws IOException {
        Hit hit = (Hit)context.get("hit");
        if (hit instanceof DocumentHit) {
            DocumentHit docHit = (DocumentHit) hit;
            if (docHit.getDocument() != null) {
                writer.write(docHit.getDocument().toXML("  "));
            }
        } else if (hit instanceof DocumentRemoveHit) {
            writeDocumentRemoveHit(writer, (DocumentRemoveHit) hit);
        } else {
            log.log(LogLevel.WARNING, "Cannot render document XML; expected hit of type " +
                    "com.yahoo.storage.searcher.Document[Remove]Hit, got " + hit.getClass().getName() +
                    ". Is there another backend searcher present?");
        }
    }

    private void writeDocumentRemoveHit(Writer writer, DocumentRemoveHit remove) throws IOException {
        writer.write("<remove documentid=\"");
        writer.write(XML.xmlEscape(remove.getIdOfRemovedDoc().toString()));
        writer.write("\"/>\n");
    }

    @Override
    public void hitFooter(com.yahoo.prelude.templates.Context context, Writer writer) throws IOException {
    }

    @Override
    public void noHits(com.yahoo.prelude.templates.Context context, Writer writer) throws IOException {
    }

}
