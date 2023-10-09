// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.ValueUpdate;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:magnarn@yahoo-inc.com">Magnar Nedland</a>
 */
public class SimpleAdapterFactoryTestCase {

    @Test
    public void requireThatCompleteUpdatesAreCombined() {
        DocumentType docType = new DocumentType("my_type");
        DocumentUpdate update = new DocumentUpdate(docType, "id:foo:my_type::1");
        Field field1 = new Field("int1", DataType.INT);
        Field field2 = new Field("int2", DataType.INT);
        Field field3 = new Field("int3", DataType.INT);
        docType.addField(field1);
        docType.addField(field2);
        docType.addField(field3);
        update.addFieldUpdate(FieldUpdate.createAssign(field1, new IntegerFieldValue(10)));
        update.addFieldUpdate(FieldUpdate.createAssign(field2, new IntegerFieldValue(20)));
        update.addFieldUpdate(FieldUpdate.createIncrement(field3, 30));

        SimpleAdapterFactory factory = new SimpleAdapterFactory();
        List<UpdateAdapter> adapters = factory.newUpdateAdapterList(update);
        assertEquals(2, adapters.size());
    }

    @Test
    public void requireThatFieldUpdateCanHaveManyPartialUpdatesForOneField() {
        DocumentType docType = new DocumentType("my_type");
        DocumentUpdate docUpdate = new DocumentUpdate(docType, "id:foo:my_type::1");
        Field field = new Field("my_int", DataType.INT);
        docType.addField(field);
        FieldUpdate fieldUpdate = FieldUpdate.create(field);
        fieldUpdate.addValueUpdate(ValueUpdate.createIncrement(1));
        fieldUpdate.addValueUpdate(ValueUpdate.createIncrement(2));
        fieldUpdate.addValueUpdate(ValueUpdate.createIncrement(4));
        docUpdate.addFieldUpdate(fieldUpdate);

        SimpleAdapterFactory factory = new SimpleAdapterFactory();
        List<UpdateAdapter> adapters = factory.newUpdateAdapterList(docUpdate);
        assertEquals(4, adapters.size());

        UpdateAdapter adapter = adapters.get(0);
        assertNotNull(adapter);
        assertEquals(new IntegerFieldValue(1), adapter.getInputValue("my_int"));
        assertNotNull(adapter = adapters.get(1));
        assertEquals(new IntegerFieldValue(2), adapter.getInputValue("my_int"));
        assertNotNull(adapter = adapters.get(2));
        assertEquals(new IntegerFieldValue(4), adapter.getInputValue("my_int"));
        assertNotNull(adapter = adapters.get(3));
        assertNull(adapter.getInputValue("my_int")); // always add an adapter for complete updates
    }
}
