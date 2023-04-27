// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.datatypes.Struct;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class PositionParserTestCase {

    @Test
    public void requireThatPositionStringsCanBeParsed() throws Exception {
        DocumentTypeManager mgr = new DocumentTypeManager();
        mgr.register(PositionDataType.INSTANCE);
        DocumentType docType = new DocumentType("my_doc");
        docType.addField("my_pos", PositionDataType.INSTANCE);
        mgr.registerDocumentType(docType);

        VespaXMLFeedReader parser = new VespaXMLFeedReader("src/test/vespaxmlparser/test_position.xml", mgr);
        Iterator<FeedOperation> it = parser.readAll().iterator();
        assertTrue(it.hasNext());
        assertDocument(PositionDataType.valueOf(1, 2), it.next());
        assertTrue(it.hasNext());
        assertDocument(PositionDataType.fromString("E3;N4"), it.next());
        assertTrue(it.hasNext());
        assertDocument(PositionDataType.fromString("5;6"), it.next());
        assertTrue(it.hasNext());
        assertDocument(PositionDataType.fromString("7;8"), it.next());
        assertFalse(it.hasNext());
    }

    private static void assertDocument(Struct expected, FeedOperation operation) {
        assertNotNull(operation);
        assertEquals(FeedOperation.Type.DOCUMENT, operation.getType());
        Document doc = operation.getDocumentPut().getDocument();
        assertNotNull(doc);
        assertEquals(expected, doc.getFieldValue("my_pos"));
    }
}
