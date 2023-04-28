// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.update.AddValueUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.ValueUpdate;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class UriParserTestCase {

    @Test
    public void requireThatUriFieldsCanBeParsed() throws Exception {
        DocumentTypeManager mgr = new DocumentTypeManager();
        DocumentType docType = new DocumentType("my_doc");
        docType.addField("my_uri", DataType.URI);
        docType.addField("my_arr", DataType.getArray(DataType.URI));
        mgr.registerDocumentType(docType);

        VespaXMLFeedReader parser = new VespaXMLFeedReader("src/test/vespaxmlparser/test_uri.xml", mgr);
        Iterator<FeedOperation> it = parser.readAll().iterator();

        Document doc = nextDocument(it);
        assertNotNull(doc);
        assertEquals(new StringFieldValue("scheme://host"), doc.getFieldValue("my_uri"));
        assertNull(doc.getFieldValue("my_arr"));

        assertNotNull(doc = nextDocument(it));
        assertNull(doc.getFieldValue("my_uri"));
        FieldValue val = doc.getFieldValue("my_arr");
        assertNotNull(val);
        assertTrue(val instanceof Array);
        Array arr = (Array)val;
        assertEquals(1, arr.size());
        assertEquals(new StringFieldValue("scheme://host"), arr.get(0));

        DocumentUpdate upd = nextUpdate(it);
        assertNotNull(upd);
        assertEquals(1, upd.fieldUpdates().size());
        FieldUpdate fieldUpd = upd.fieldUpdates().iterator().next();
        assertNotNull(fieldUpd);
        assertEquals(docType.getField("my_arr"), fieldUpd.getField());
        assertEquals(1, fieldUpd.getValueUpdates().size());
        ValueUpdate valueUpd = fieldUpd.getValueUpdate(0);
        assertNotNull(valueUpd);
        assertTrue(valueUpd instanceof AddValueUpdate);
        assertEquals(new StringFieldValue("scheme://host"), valueUpd.getValue());

        assertFalse(it.hasNext());
    }

    private static Document nextDocument(Iterator<FeedOperation> it) {
        assertTrue(it.hasNext());
        FeedOperation op = it.next();
        assertNotNull(op);
        assertEquals(FeedOperation.Type.DOCUMENT, op.getType());
        Document doc = op.getDocumentPut().getDocument();
        assertNotNull(doc);
        return doc;
    }

    private static DocumentUpdate nextUpdate(Iterator<FeedOperation> it) {
        assertTrue(it.hasNext());
        FeedOperation op = it.next();
        assertNotNull(op);
        assertEquals(FeedOperation.Type.UPDATE, op.getType());
        DocumentUpdate upd = op.getDocumentUpdate();
        assertNotNull(upd);
        return upd;
    }
}
