// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import com.yahoo.document.fieldpathupdate.RemoveFieldPathUpdate;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests that an element assign, e.g. <code>"my_str[0]": {"assign": "HELLO"}</code>, is run
 * through the indexing script like any put value, instead of being passed through untouched.
 *
 * @author johsol
 */
public class ElementAssignUpdateTestCase {

    private static DocumentType newDocumentType() {
        DocumentType docType = new DocumentType("my_type");
        docType.addField(new Field("my_str", DataType.getArray(DataType.STRING)));
        docType.addField(new Field("my_map", new MapDataType(DataType.STRING, DataType.INT)));
        return docType;
    }

    @Test
    public void requireThatElementAssignIsRecognized() {
        DocumentType docType = newDocumentType();
        assertTrue(FieldPathUpdateHelper.isElementAssign(
                new AssignFieldPathUpdate(docType, "my_str[0]", "", new StringFieldValue("foo"))));
    }

    @Test
    public void requireThatOtherFieldPathUpdatesAreNotRecognizedAsElementAssign() {
        DocumentType docType = newDocumentType();

        // Whole-field assign is handled by isFieldValues, not as an element assign.
        Array<StringFieldValue> array = new Array<>(docType.getField("my_str").getDataType());
        array.add(new StringFieldValue("foo"));
        assertFalse(FieldPathUpdateHelper.isElementAssign(
                new AssignFieldPathUpdate(docType, "my_str", "", array)));

        // Expression assigns compute from the stored document and cannot be pre-processed.
        assertFalse(FieldPathUpdateHelper.isElementAssign(
                new AssignFieldPathUpdate(docType, "my_str[0]", "", "5")));

        // Conditional assigns depend on the stored document.
        assertFalse(FieldPathUpdateHelper.isElementAssign(
                new AssignFieldPathUpdate(docType, "my_str[0]", "my_type.my_str == \"foo\"",
                                          new StringFieldValue("foo"))));

        // Map key paths are not element assigns.
        assertFalse(FieldPathUpdateHelper.isElementAssign(
                new AssignFieldPathUpdate(docType, "my_map{a}", "", new IntegerFieldValue(1))));

        // Removes carry no value to process.
        assertFalse(FieldPathUpdateHelper.isElementAssign(
                new RemoveFieldPathUpdate(docType, "my_str[0]", "")));
    }

    @Test
    public void requireThatElementAssignIsProcessedByIndexingScript() throws ParseException {
        DocumentType docType = newDocumentType();
        DocumentUpdate upd = new DocumentUpdate(docType, "id:scheme:my_type::");
        upd.addFieldPathUpdate(new AssignFieldPathUpdate(docType, "my_str[0]", "",
                                                         new StringFieldValue("HELLO")));

        upd = Expression.execute(Expression.fromString("input my_str | for_each { lowercase } | index my_str"), upd);

        assertNotNull(upd);
        assertEquals(1, upd.fieldPathUpdates().size());
        FieldPathUpdate out = upd.fieldPathUpdates().iterator().next();
        assertTrue(out instanceof AssignFieldPathUpdate);
        assertEquals("my_str[0]", out.getOriginalFieldPath());
        assertEquals(new StringFieldValue("hello"), ((AssignFieldPathUpdate)out).getNewValue());
    }

    @Test
    public void requireThatExpressionAssignToElementIsPassedThrough() throws ParseException {
        DocumentType docType = newDocumentType();
        DocumentUpdate upd = new DocumentUpdate(docType, "id:scheme:my_type::");
        upd.addFieldPathUpdate(new AssignFieldPathUpdate(docType, "my_str[0]", "", "5"));

        upd = Expression.execute(Expression.fromString("input my_str | for_each { lowercase } | index my_str"), upd);

        assertNotNull(upd);
        assertEquals(1, upd.fieldPathUpdates().size());
        FieldPathUpdate out = upd.fieldPathUpdates().iterator().next();
        assertTrue(out instanceof AssignFieldPathUpdate);
        assertEquals("5", ((AssignFieldPathUpdate)out).getExpression());
    }

}
