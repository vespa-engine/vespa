// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.RemoveFieldPathUpdate;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author johsol
 */
public class ElementAssignUpdateTestCase {

    private static final DocumentType docType = newDocumentType();

    @Test
    public void require_that_element_assign_recognized() {
        var upd = update("my_str[0]", "foo");
        assertTrue(FieldPathUpdateHelper.isElementAssign(upd));
    }

    @Test
    public void require_that_other_field_path_updates_not_recognized() {
        // Whole-field assign is handled by isFieldValues, not as an element assign.
        var array = new Array<>(docType.getField("my_str").getDataType());
        array.add(stringValue("foo"));
        assertFalse(FieldPathUpdateHelper.isElementAssign(update("my_str", array)));

        // Expression assigns compute from the stored document and cannot be pre-processed.
        assertFalse(FieldPathUpdateHelper.isElementAssign(new AssignFieldPathUpdate(docType, "my_str[0]", "", "5")));

        // Conditional assigns depend on the stored document.
        assertFalse(FieldPathUpdateHelper.isElementAssign(
                new AssignFieldPathUpdate(docType, "my_str[0]", "my_type.my_str == \"foo\"",
                                          stringValue("foo"))));

        // Map key paths are not element assigns.
        assertFalse(FieldPathUpdateHelper.isElementAssign(update("my_map{a}", 1)));

        // Removes carry no value to process.
        assertFalse(FieldPathUpdateHelper.isElementAssign(new RemoveFieldPathUpdate(docType, "my_str[0]", "")));

        // Only top level array fields are handled: a path into an element is not an element assign.
        assertFalse(FieldPathUpdateHelper.isElementAssign(update("my_structs[0].s", "foo")));
    }

    @Test
    public void require_that_element_assign_processed_by_indexing_script() throws ParseException {
        var fpupd = update("my_str[0]", "HELLO");

        var upd = new DocumentUpdate(docType, "id:scheme:my_type::");
        upd.addFieldPathUpdate(fpupd);
        upd = execute("input my_str | for_each { lowercase } | index my_str", upd);
        assertNotNull(upd);
        assertEquals(1, upd.fieldPathUpdates().size());

        var out = upd.fieldPathUpdates().iterator().next();
        assertEquals(update("my_str[0]", "hello"), out);
    }

    @Test
    public void require_two_element_assigns_processed() throws ParseException {
        var upd = new DocumentUpdate(docType, "id:scheme:my_type::");
        upd.addFieldPathUpdate(update("my_str[0]", "HELLO"));
        upd.addFieldPathUpdate(update("my_str[1]", "WORLD"));

        upd = execute("input my_str | for_each { lowercase } | index my_str", upd);
        assertNotNull(upd);
        assertEquals(2, upd.fieldPathUpdates().size());

        var it = upd.fieldPathUpdates().iterator();
        assertEquals(update("my_str[0]", "hello"), it.next());
        assertEquals(update("my_str[1]", "world"), it.next());
    }

    @Test
    public void require_that_element_index_preserved() throws ParseException {
        var fpupd = update("my_str[1000]", "HELLO");

        var upd = new DocumentUpdate(docType, "id:scheme:my_type::");
        upd.addFieldPathUpdate(fpupd);
        upd = execute("input my_str | for_each { lowercase } | index my_str", upd);
        assertNotNull(upd);
        assertEquals(1, upd.fieldPathUpdates().size());

        var out = upd.fieldPathUpdates().iterator().next();
        assertEquals(update("my_str[1000]", "hello"), out);
    }

    @Test
    public void require_that_element_assign_keeps_flags() throws ParseException {
        var fpupd = update("my_int[1]", 0);
        fpupd.setRemoveIfZero(true);
        fpupd.setCreateMissingPath(false);

        var upd = new DocumentUpdate(docType, "id:scheme:my_type::");
        upd.addFieldPathUpdate(fpupd);
        upd = execute("input my_int | attribute my_int", upd);
        assertNotNull(upd);
        assertEquals(1, upd.fieldPathUpdates().size());

        var out = (AssignFieldPathUpdate)upd.fieldPathUpdates().iterator().next();
        assertEquals("my_int[1]", out.getOriginalFieldPath());
        assertEquals(intValue(0), out.getNewValue());
        assertTrue(out.getRemoveIfZero());
        assertFalse(out.getCreateMissingPath());
    }

    @Test
    public void require_that_element_assign_array_of_struct() throws ParseException {
        var element = new Struct(newStructType());
        element.setFieldValue("s", stringValue("foo"));
        var fpupd = update("my_structs[1]", element);

        var upd = new DocumentUpdate(docType, "id:scheme:my_type::");
        upd.addFieldPathUpdate(fpupd);
        upd = execute("input my_structs | passthrough my_structs", upd);
        assertNotNull(upd);
        assertEquals(1, upd.fieldPathUpdates().size());

        var out = (AssignFieldPathUpdate)upd.fieldPathUpdates().iterator().next();
        assertEquals("my_structs[1]", out.getOriginalFieldPath());
        assertEquals(element, out.getNewValue());
    }

    // -------------- HELPERS ---------------------

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

    private static StringFieldValue stringValue(String s) {
        return new StringFieldValue(s);
    }

    private static IntegerFieldValue intValue(int s) {
        return new IntegerFieldValue(s);
    }

    private static AssignFieldPathUpdate update(String fieldPath, FieldValue value) {
        return new AssignFieldPathUpdate(docType, fieldPath, null, value);
    }

    private static AssignFieldPathUpdate update(String fieldPath, String value) {
        return update(fieldPath, stringValue(value));
    }

    private static AssignFieldPathUpdate update(String fieldPath, int value) {
        return update(fieldPath, intValue(value));
    }

    private static DocumentUpdate execute(String script, DocumentUpdate upd) throws ParseException {
        return Expression.execute(Expression.fromString(script), upd);
    }

}
