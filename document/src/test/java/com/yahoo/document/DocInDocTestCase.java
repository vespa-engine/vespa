// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.serialization.DocumentDeserializer;
import com.yahoo.document.serialization.DocumentDeserializerFactory;
import com.yahoo.document.serialization.DocumentSerializer;
import com.yahoo.document.serialization.DocumentSerializerFactory;
import com.yahoo.io.GrowableByteBuffer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

/**
 * @author Einar M R Rosenvinge
 */
public class DocInDocTestCase {

    @Test
    public void testDocInDoc() {
        DocumentTypeManager manager = new DocumentTypeManager();
        var sub = DocumentTypeManagerConfigurer.configure(manager, "file:src/test/java/com/yahoo/document/documentmanager.docindoc.cfg");
        sub.close();
        Document inner1 = new Document(manager.getDocumentType("docindoc"), "id:inner:docindoc::one");
        inner1.setFieldValue("name", new StringFieldValue("Donald Duck"));
        inner1.setFieldValue("content", new StringFieldValue("Lives in Duckburg"));
        Document inner2 = new Document(manager.getDocumentType("docindoc"), "id:inner:docindoc::two");
        inner2.setFieldValue("name", new StringFieldValue("Uncle Scrooge"));
        inner2.setFieldValue("content", new StringFieldValue("Lives in Duckburg, too."));

        Array<Document> innerArray = (Array<Document>) manager.getDocumentType("outerdoc").getField("innerdocuments").getDataType().createFieldValue();
        innerArray.add(inner1);
        innerArray.add(inner2);

        Document outer = new Document(manager.getDocumentType("outerdoc"), "id:outer:outerdoc::the:only:one");
        outer.setFieldValue("innerdocuments", innerArray);

        DocumentSerializer serializer = DocumentSerializerFactory.create6();
        serializer.write(outer);

        GrowableByteBuffer buf = serializer.getBuf();
        buf.flip();

        DocumentDeserializer deserializer = DocumentDeserializerFactory.create6(manager, buf);
        Document outerDeserialized = new Document(deserializer);

        assertEquals(outer, outerDeserialized);
        assertNotSame(outer, outerDeserialized);
    }

}
