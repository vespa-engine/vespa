// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.update.AddValueUpdate;
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

        DocumentUpdate docUpdate = new DocumentUpdate(docType, "doc:scheme:");
        docUpdate.addFieldUpdate(FieldUpdate.createAdd(docType.getField("my_str"), new StringFieldValue("6;9")));
        docUpdate = Expression.execute(Expression.fromString("input my_str | for_each { to_pos } | index my_pos"), docUpdate);

        assertNotNull(docUpdate);
        assertEquals(0, docUpdate.getFieldPathUpdates().size());
        assertEquals(1, docUpdate.getFieldUpdates().size());

        FieldUpdate fieldUpd = docUpdate.getFieldUpdate(0);
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
        DocumentUpdate upd = new DocumentUpdate(docType, "doc:scheme:");
        upd.addFieldUpdate(FieldUpdate.createAdd(docType.getField("my_str"), new StringFieldValue("foo")));
        upd.setCreateIfNonExistent(true);

        upd = Expression.execute(Expression.fromString("input my_str | index my_str"), upd);
        assertTrue(upd.getCreateIfNonExistent());
    }
}
