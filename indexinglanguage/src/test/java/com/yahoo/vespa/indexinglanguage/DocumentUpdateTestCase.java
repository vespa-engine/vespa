// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.update.AddValueUpdate;
import com.yahoo.document.update.AssignValueUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.ValueUpdate;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings({ "rawtypes" })
public class DocumentUpdateTestCase {

    @Test
    public void requireThatArrayOfStructElementAddIsProcessedCorrectly() throws ParseException {
        DocumentType docType = new DocumentType("my_input");
        docType.addField(new Field("my_str", DataType.getArray(DataType.STRING)));
        docType.addField(new Field("my_pos", DataType.getArray(PositionDataType.INSTANCE)));

        DocumentUpdate docUpdate = new DocumentUpdate(docType, "id:scheme:my_input::");
        docUpdate.addFieldUpdate(FieldUpdate.createAdd(docType.getField("my_str"), new StringFieldValue("6;9")));
        docUpdate = Expression.execute(Expression.fromString("input my_str | for_each { to_pos } | index my_pos"), docUpdate);

        assertNotNull(docUpdate);
        assertEquals(0, docUpdate.fieldPathUpdates().size());
        assertEquals(1, docUpdate.fieldUpdates().size());

        FieldUpdate fieldUpd = docUpdate.fieldUpdates().iterator().next();
        assertNotNull(fieldUpd);
        assertEquals(docType.getField("my_pos"), fieldUpd.getField());
        assertEquals(1, fieldUpd.getValueUpdates().size());

        ValueUpdate valueUpd = fieldUpd.getValueUpdate(0);
        assertNotNull(valueUpd);
        assertTrue(valueUpd instanceof AddValueUpdate);

        Object val = valueUpd.getValue();
        assertNotNull(val);
        assertTrue(val instanceof Struct);
        Struct pos = (Struct)val;
        assertEquals(PositionDataType.INSTANCE, pos.getDataType());
        assertEquals(new IntegerFieldValue(6), PositionDataType.getXValue(pos));
        assertEquals(new IntegerFieldValue(9), PositionDataType.getYValue(pos));
    }

    @Test
    public void requireThatCreateIfNonExistentFlagIsPropagated() throws ParseException {
        DocumentType docType = new DocumentType("my_input");
        docType.addField(new Field("my_str", DataType.getArray(DataType.STRING)));
        DocumentUpdate upd = new DocumentUpdate(docType, "id:scheme:my_input::");
        upd.addFieldUpdate(FieldUpdate.createAdd(docType.getField("my_str"), new StringFieldValue("foo")));
        upd.setCreateIfNonExistent(true);

        upd = Expression.execute(Expression.fromString("input my_str | index my_str"), upd);
        assertTrue(upd.getCreateIfNonExistent());
    }

    @Test
    public void assign_updates_to_structs_are_preserved() throws ParseException {
        var docType = new DocumentType("my_input");
        var structType = new StructDataType("foobarstruct");
        var fooField = new Field("foo", DataType.STRING);
        var barField = new Field("bar", DataType.STRING);
        structType.addField(fooField);
        structType.addField(barField);
        docType.addField(new Field("mystruct", structType));

        var upd = new DocumentUpdate(docType, "id:scheme:my_input::");
        var updatedStruct = new Struct(structType);
        updatedStruct.setFieldValue("foo", new StringFieldValue("new groovy value"));
        updatedStruct.setFieldValue("bar", new StringFieldValue("totally tubular!"));
        upd.addFieldUpdate(FieldUpdate.createAssign(docType.getField("mystruct"), updatedStruct));

        upd = Expression.execute(Expression.fromString("input mystruct | passthrough mystruct"), upd);
        assertEquals(upd.fieldUpdates().size(), 1);
        var fieldUpdate = upd.getFieldUpdate("mystruct");
        assertNotNull(fieldUpdate);
        var valueUpdate = fieldUpdate.getValueUpdate(0);
        assertTrue(valueUpdate instanceof AssignValueUpdate);
        var av = (AssignValueUpdate)valueUpdate;
        assertEquals(av.getValue(), updatedStruct);
    }
}
