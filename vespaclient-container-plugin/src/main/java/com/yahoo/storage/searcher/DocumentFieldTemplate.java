// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.storage.searcher;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.Raw;
import com.yahoo.io.ByteWriter;
import com.yahoo.prelude.templates.Context;
import com.yahoo.text.XML;

import java.io.IOException;
import java.io.Writer;

/**
 * Template used to render a single field for a single Document. Fields
 * that are either of type CONTENT or RAW are written directly, while
 * all other fields are wrapped in Vespa XML and escaped.
 *
 * @deprecated use a renderer instead
 */
@Deprecated // TODO: Remove on Vespa 7
@SuppressWarnings("deprecation")
public class DocumentFieldTemplate extends com.yahoo.prelude.templates.UserTemplate<Writer> {

    Field field;
    String contentType;
    String encoding;
    boolean wrapXml;

    public DocumentFieldTemplate(Field field, String contentType, String encoding, boolean wrapXml) {
        super("documentfield", contentType, encoding);
        this.field = field;
        this.contentType = contentType;
        this.encoding = encoding;
        this.wrapXml = wrapXml;
    }

    @Override
    public void error(Context context, Writer writer) throws IOException {
        // Error shouldn't be handled by this template, but rather
        // delegated to the searcher
    }

    @Override
    public Writer wrapWriter(Writer writer) {
        /* TODO: uncomment
        if (!(writer instanceof ByteWriter)) {
            throw new IllegalArgumentException("ByteWriter required, but got " + writer.getClass().getName());
        }
        */

        return writer;
    }

    @Override
    public void header(Context context, Writer writer) throws IOException {
        if (wrapXml) {
            // XML wrapping should only be used for default field rendering
            writer.write("<?xml version=\"1.0\" encoding=\"" + encoding + "\"?>\n");
            writer.write("<result>");
        }
    }

    @Override
    public void footer(Context context, Writer writer) throws IOException {
        if (wrapXml) {
            writer.write("</result>\n");
        }
    }

    @Override
    public void hit(Context context, Writer writer) throws IOException {
        DocumentHit hit = (DocumentHit)context.get("hit");
        Document doc = hit.getDocument();
        // Assume field existence has been checked before we ever get here.
        // Also assume that relevant encoding/content type is set
        // appropriately according to the request and the field's content
        // type, as this is immutable in the template set.
        FieldValue value = doc.getFieldValue(field);
        if (field.getDataType() == DataType.RAW) {
            ByteWriter bw = (ByteWriter)writer;
            bw.append(((Raw) value).getByteBuffer().array());
        } else {
            writer.write(XML.xmlEscape(value.toString(), false));
        }
    }

    @Override
    public void hitFooter(Context context, Writer writer) throws IOException {
    }

    @Override
    public void noHits(Context context, Writer writer) throws IOException {
    }

}
