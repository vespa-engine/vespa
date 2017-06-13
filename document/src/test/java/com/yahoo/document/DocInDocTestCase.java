// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.serialization.*;
import com.yahoo.io.GrowableByteBuffer;
import junit.framework.TestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class DocInDocTestCase extends TestCase {

    @Test
    public void testDocInDoc() {
        DocumentTypeManager manager = new DocumentTypeManager();
        DocumentTypeManagerConfigurer.configure(manager, "file:src/test/java/com/yahoo/document/documentmanager.docindoc.cfg");

        Document inner1 = new Document(manager.getDocumentType("docindoc"), "doc:inner:number:one");
        inner1.setFieldValue("name", new StringFieldValue("Donald Duck"));
        inner1.setFieldValue("content", new StringFieldValue("Lives in Duckburg"));
        Document inner2 = new Document(manager.getDocumentType("docindoc"), "doc:inner:number:two");
        inner2.setFieldValue("name", new StringFieldValue("Uncle Scrooge"));
        inner2.setFieldValue("content", new StringFieldValue("Lives in Duckburg, too."));

        Array<Document> innerArray = (Array<Document>) manager.getDocumentType("outerdoc").getField("innerdocuments").getDataType().createFieldValue();
        innerArray.add(inner1);
        innerArray.add(inner2);

        Document outer = new Document(manager.getDocumentType("outerdoc"), "doc:outer:the:only:one");
        outer.setFieldValue("innerdocuments", innerArray);

        DocumentSerializer serializer = DocumentSerializerFactory.create42();
        serializer.write(outer);

        GrowableByteBuffer buf = serializer.getBuf();
        buf.flip();

        DocumentDeserializer deserializer = DocumentDeserializerFactory.create42(manager, buf);
        Document outerDeserialized = new Document(deserializer);

        assertEquals(outer, outerDeserialized);
        assertNotSame(outer, outerDeserialized);

    }
}
