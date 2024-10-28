// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.vespa.indexinglanguage.expressions.ExecutionContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests converting a map of struct into an array of string.
 *
 * @author bratseth
 */
public class MapConversionTestCase {

    @Test
    @SuppressWarnings("unchecked")
    public void testMapConversion() {
        var tester = new ScriptTester();
        var expression = tester.expressionFrom("input myMap | for_each { get_field $key . \":\" . get_field myString } | index myArray");

        var structType = new StructDataType("myStruct");
        var stringField = new Field("myString", DataType.STRING);
        structType.addField(stringField);
        var mapType = new MapDataType(DataType.STRING, structType);

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("myMap", mapType));
        adapter.createField(new Field("myArray", new ArrayDataType(DataType.STRING)));
        var mapValue = new MapFieldValue<StringFieldValue, Struct>(mapType);
        var struct1 = new Struct(structType);
        struct1.setFieldValue(stringField, "value1");
        mapValue.put(new StringFieldValue("key1"), struct1);
        var struct2 = new Struct(structType);
        struct2.setFieldValue(stringField, "value2");
        mapValue.put(new StringFieldValue("key2"), struct2);
        adapter.setValue("myMap", mapValue);

        expression.setStatementOutput(new DocumentType("myDocument"),
                                      new Field("myArray", new ArrayDataType(DataType.STRING)));

        expression.verify(adapter);

        ExecutionContext context = new ExecutionContext(adapter);
        expression.execute(context);
        assertTrue(adapter.values.containsKey("myArray"));
        var arrayValue = (Array<StringFieldValue>)adapter.values.get("myArray");
        assertEquals(2, arrayValue.size());
        assertEquals("key1:value1", arrayValue.get(0).getString());
        assertEquals("key2:value2", arrayValue.get(1).getString());
    }

}
