// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class DocumentToPathUpdateTestCase {

    @Test
    public void requireThatIntegerAssignIsConverted() {
        DocumentType docType = new DocumentType("my_type");
        docType.addField(new Field("my_int", DataType.INT));

        FieldPathUpdate upd = new AssignFieldPathUpdate(docType, "my_int", "", new IntegerFieldValue(69));
        Document doc = FieldPathUpdateHelper.newPartialDocument(null, upd);
        assertNotNull(doc);
        doc.setFieldValue("my_int", new IntegerFieldValue(96));

        DocumentUpdate docUpd = new FieldPathUpdateAdapter(new SimpleDocumentAdapter(null, doc), upd).getOutput();
        assertNotNull(docUpd);
        assertEquals(1, docUpd.fieldPathUpdates().size());
        assertNotNull(upd = docUpd.fieldPathUpdates().iterator().next());

        assertTrue(upd instanceof AssignFieldPathUpdate);
        assertEquals("my_int", upd.getOriginalFieldPath());
        assertEquals(new IntegerFieldValue(96), ((AssignFieldPathUpdate)upd).getNewValue());
    }

    @Test
    public void requireThatStringAssignIsConverted() {
        DocumentType docType = new DocumentType("my_type");
        docType.addField(new Field("my_str", DataType.STRING));

        FieldPathUpdate upd = new AssignFieldPathUpdate(docType, "my_str", "", new StringFieldValue("69"));
        Document doc = FieldPathUpdateHelper.newPartialDocument(null, upd);
        assertNotNull(doc);
        doc.setFieldValue("my_str", new StringFieldValue("96"));

        DocumentUpdate docUpd = new FieldPathUpdateAdapter(new SimpleDocumentAdapter(null, doc), upd).getOutput();
        assertNotNull(docUpd);
        assertEquals(1, docUpd.fieldPathUpdates().size());
        assertNotNull(upd = docUpd.fieldPathUpdates().iterator().next());

        assertTrue(upd instanceof AssignFieldPathUpdate);
        assertEquals("my_str", upd.getOriginalFieldPath());
        assertEquals(new StringFieldValue("96"), ((AssignFieldPathUpdate)upd).getNewValue());
    }

    @Test
    public void requireThatStructAssignIsConverted() {
        DocumentType docType = new DocumentType("my_type");
        StructDataType structType = new StructDataType("my_struct");
        structType.addField(new Field("b", DataType.INT));
        docType.addField(new Field("a", structType));

        Struct struct = structType.createFieldValue();
        struct.setFieldValue("b", new IntegerFieldValue(69));
        FieldPathUpdate upd = new AssignFieldPathUpdate(docType, "a", "", struct);
        Document doc = FieldPathUpdateHelper.newPartialDocument(null, upd);
        assertNotNull(doc);

        FieldValue obj = doc.getFieldValue("a");
        assertTrue(obj instanceof Struct);
        struct = (Struct)obj;
        struct.setFieldValue("b", new IntegerFieldValue(96));

        DocumentUpdate docUpd = new FieldPathUpdateAdapter(new SimpleDocumentAdapter(null, doc), upd).getOutput();
        assertNotNull(docUpd);
        assertEquals(1, docUpd.fieldPathUpdates().size());
        assertNotNull(upd = docUpd.fieldPathUpdates().iterator().next());

        assertTrue(upd instanceof AssignFieldPathUpdate);
        assertEquals("a", upd.getOriginalFieldPath());
        assertEquals(struct, ((AssignFieldPathUpdate)upd).getNewValue());
    }

    @Test
    public void requireThatStructElementAssignIsConverted() {
        DocumentType docType = new DocumentType("my_type");
        StructDataType structType = new StructDataType("my_struct");
        structType.addField(new Field("b", DataType.INT));
        docType.addField(new Field("a", structType));

        FieldPathUpdate upd = new AssignFieldPathUpdate(docType, "a.b", "", new IntegerFieldValue(69));
        Document doc = FieldPathUpdateHelper.newPartialDocument(null, upd);
        assertNotNull(doc);

        FieldValue obj = doc.getFieldValue("a");
        assertTrue(obj instanceof Struct);
        Struct struct = (Struct)obj;
        struct.setFieldValue("b", new IntegerFieldValue(96));

        DocumentUpdate docUpd = new FieldPathUpdateAdapter(new SimpleDocumentAdapter(null, doc), upd).getOutput();
        assertNotNull(docUpd);
        assertEquals(1, docUpd.fieldPathUpdates().size());
        assertNotNull(upd = docUpd.fieldPathUpdates().iterator().next());

        assertTrue(upd instanceof AssignFieldPathUpdate);
        assertEquals("a.b", upd.getOriginalFieldPath());
        obj = ((AssignFieldPathUpdate)upd).getNewValue();
        assertTrue(obj instanceof IntegerFieldValue);
        assertEquals(96, ((IntegerFieldValue)obj).getInteger());
    }
}
