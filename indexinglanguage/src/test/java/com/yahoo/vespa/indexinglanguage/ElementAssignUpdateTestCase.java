// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
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
        docType.addField(new Field("my_int", DataType.getArray(DataType.INT)));
        docType.addField(new Field("my_structs", DataType.getArray(newStructType())));
        docType.addField(new Field("my_map", new MapDataType(DataType.STRING, DataType.INT)));
        return docType;
    }

    private static StructDataType newStructType() {
        StructDataType structType = new StructDataType("my_struct");
        structType.addField(new Field("s", DataType.STRING));
        return structType;
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

        // Only top level array fields are handled: a path into an element is not an element assign.
        assertFalse(FieldPathUpdateHelper.isElementAssign(
                new AssignFieldPathUpdate(docType, "my_structs[0].s", "", new StringFieldValue("foo"))));
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

    /**
     * The script processes the assigned value only, so the flags deciding how the assign is applied
     * must survive it. An assigned zero with removeIfZero set removes the element instead of setting it.
     */
    @Test
    public void requireThatElementAssignKeepsFlagsOfTheUpdateAsFed() throws ParseException {
        DocumentType docType = newDocumentType();
        DocumentUpdate upd = new DocumentUpdate(docType, "id:scheme:my_type::");
        AssignFieldPathUpdate assign = new AssignFieldPathUpdate(docType, "my_int[1]", "",
                                                                 new IntegerFieldValue(0));
        assign.setRemoveIfZero(true);
        assign.setCreateMissingPath(false);
        upd.addFieldPathUpdate(assign);

        upd = Expression.execute(Expression.fromString("input my_int | attribute my_int"), upd);

        assertNotNull(upd);
        assertEquals(1, upd.fieldPathUpdates().size());
        AssignFieldPathUpdate out = (AssignFieldPathUpdate)upd.fieldPathUpdates().iterator().next();
        assertEquals("my_int[1]", out.getOriginalFieldPath());
        assertEquals(new IntegerFieldValue(0), out.getNewValue());
        assertTrue(out.getRemoveIfZero());
        assertFalse(out.getCreateMissingPath());
    }

    /** The element of an array of struct must survive being wrapped and unwrapped. */
    @Test
    public void requireThatElementAssignOfArrayOfStructIsProcessed() throws ParseException {
        DocumentType docType = newDocumentType();
        Struct element = new Struct(newStructType());
        element.setFieldValue("s", new StringFieldValue("foo"));

        DocumentUpdate upd = new DocumentUpdate(docType, "id:scheme:my_type::");
        upd.addFieldPathUpdate(new AssignFieldPathUpdate(docType, "my_structs[1]", "", element));

        upd = Expression.execute(Expression.fromString("input my_structs | passthrough my_structs"), upd);

        assertNotNull(upd);
        assertEquals(1, upd.fieldPathUpdates().size());
        AssignFieldPathUpdate out = (AssignFieldPathUpdate)upd.fieldPathUpdates().iterator().next();
        assertEquals("my_structs[1]", out.getOriginalFieldPath());
        assertEquals(element, out.getNewValue());
    }

}
