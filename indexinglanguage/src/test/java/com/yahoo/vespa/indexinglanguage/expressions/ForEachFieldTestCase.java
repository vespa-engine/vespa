// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.annotation.SpanTrees;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import com.yahoo.vespa.indexinglanguage.linguistics.AnnotatorConfig;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerifyThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

/**
 * @author arnej
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class ForEachFieldTestCase {

    private static final StructDataType normal = new StructDataType("normal");
    static {
        normal.addField(new Field("mykey", DataType.INT));
        normal.addField(new Field("myvalue", DataType.STRING));
    }
    private static final ArrayDataType arrayOfNormal = new ArrayDataType(normal);
    private static final MapDataType mapOfString = new MapDataType(DataType.STRING, DataType.STRING);
    private static final MapDataType mapOfNormal = new MapDataType(DataType.STRING, normal);

    @Test
    public void requireThatAccessorsWork() {
        Expression innerExp = new LowerCaseExpression();
        ForEachFieldExpression exp = new ForEachFieldExpression("myvalue", innerExp);
        assertEquals("myvalue", exp.getPath());
        assertSame(innerExp, exp.getInnerExpression());
        assertEquals("for_each_field \"myvalue\" { lowercase }", exp.toString());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new ForEachFieldExpression("myvalue", new LowerCaseExpression());
        assertFalse(exp.equals(new Object()));
        assertFalse(exp.equals(new ForEachFieldExpression("other", new LowerCaseExpression())));
        assertFalse(exp.equals(new ForEachFieldExpression("myvalue", new TrimExpression())));
        assertEquals(exp, new ForEachFieldExpression("myvalue", new LowerCaseExpression()));
        assertEquals(exp.hashCode(), new ForEachFieldExpression("myvalue", new LowerCaseExpression()).hashCode());
    }

    @Test
    public void requireThatTypesAreVerified() {
        assertVerify(arrayOfNormal, new ForEachFieldExpression("myvalue", new LowerCaseExpression()), arrayOfNormal);
        assertVerify(normal, new ForEachFieldExpression("myvalue", new LowerCaseExpression()), normal);
        assertVerify(mapOfString, new ForEachFieldExpression("value", new LowerCaseExpression()), mapOfString);
        assertVerify(mapOfString, new ForEachFieldExpression("key", new LowerCaseExpression()), mapOfString);
        assertVerify(mapOfNormal, new ForEachFieldExpression("value.myvalue", new LowerCaseExpression()), mapOfNormal);

        assertVerifyThrows("Invalid expression 'lowercase': Expected string input, got int",
                           new ForEachFieldExpression("mykey", new LowerCaseExpression()), arrayOfNormal,
                           new TypeContext(new SimpleTestAdapter()));
        assertVerifyThrows("Invalid expression 'for_each_field \"nosuch\" { lowercase }': Struct 'normal' has no field 'nosuch'",
                           new ForEachFieldExpression("nosuch", new LowerCaseExpression()), arrayOfNormal,
                           new TypeContext(new SimpleTestAdapter()));
        assertVerifyThrows("Invalid expression 'for_each_field \"foo\" { lowercase }': Expected 'key' or 'value' to select from map Map<string,string>, got 'foo'",
                           new ForEachFieldExpression("foo", new LowerCaseExpression()), mapOfString,
                           new TypeContext(new SimpleTestAdapter()));
        assertVerifyThrows("Invalid expression 'for_each_field \"myvalue\" { lowercase }': Expected Array, Struct or Map input when selecting 'myvalue', got string",
                           new ForEachFieldExpression("myvalue", new LowerCaseExpression()), DataType.STRING,
                           new TypeContext(new SimpleTestAdapter()));
    }

    @Test
    public void requireThatOnlySelectedFieldOfEachArrayElementIsConverted() {
        Array<Struct> input = new Array<>(arrayOfNormal);
        input.add(normal(1, "Foo"));
        input.add(normal(2, null)); // missing field
        input.add(normal(3, "BAR"));

        FieldValue output = execute(new ForEachFieldExpression("myvalue", new LowerCaseExpression()), input);

        assertEquals(arrayOfNormal, output.getDataType());
        Array<Struct> result = (Array<Struct>)output;
        assertEquals(3, result.size());
        assertEquals(normal(1, "foo"), result.get(0));
        assertEquals(normal(2, null), result.get(1));
        assertEquals(normal(3, "bar"), result.get(2));
        // The input is left unchanged
        assertEquals(normal(1, "Foo"), input.get(0));
        // Fields not on the path are shared, not copied
        assertSame(input.get(0).getFieldValue("mykey"), result.get(0).getFieldValue("mykey"));
    }

    @Test
    public void requireThatAnyTypeIsAccepted() {
        var exp = new ForEachFieldExpression("myvalue", new LowerCaseExpression());
        var context = new TypeContext(new SimpleTestAdapter());
        assertEquals(AnyDataType.instance, exp.setInputType(AnyDataType.instance, context));
        assertEquals(AnyDataType.instance, exp.setOutputType(AnyDataType.instance, context));
    }

    @Test
    public void requireThatMapValuesOrKeysAreConverted() {
        MapFieldValue<StringFieldValue, StringFieldValue> input = new MapFieldValue<>(mapOfString);
        input.put(new StringFieldValue("Key"), new StringFieldValue("Value"));

        MapFieldValue values = (MapFieldValue)execute(new ForEachFieldExpression("value", new LowerCaseExpression()), input);
        assertEquals(mapOfString, values.getDataType());
        assertEquals(new StringFieldValue("value"), values.get(new StringFieldValue("Key")));

        MapFieldValue keys = (MapFieldValue)execute(new ForEachFieldExpression("key", new LowerCaseExpression()), input);
        assertEquals(new StringFieldValue("Value"), keys.get(new StringFieldValue("key")));
    }

    @Test
    public void requireThatArraysAtTheEndOfThePathAreTraversed() {
        StructDataType type = new StructDataType("tagged");
        type.addField(new Field("tags", new ArrayDataType(DataType.STRING)));
        ArrayDataType arrayOfTagged = new ArrayDataType(type);
        assertVerify(type, new ForEachFieldExpression("tags", new LowerCaseExpression()), type);
        assertVerify(arrayOfTagged, new ForEachFieldExpression("tags", new LowerCaseExpression()), arrayOfTagged);

        Struct struct = new Struct(type);
        Array<StringFieldValue> tags = new Array<>(type.getField("tags").getDataType());
        tags.add(new StringFieldValue("Foo"));
        tags.add(new StringFieldValue("BAR"));
        struct.setFieldValue("tags", tags);
        Array<Struct> input = new Array<>(arrayOfTagged);
        input.add(struct);

        Array<Struct> output = (Array<Struct>)execute(new ForEachFieldExpression("tags", new LowerCaseExpression()), input);
        Array<StringFieldValue> result = (Array<StringFieldValue>)output.get(0).getFieldValue("tags");
        assertEquals(tags.getDataType(), result.getDataType());
        assertEquals(new StringFieldValue("foo"), result.get(0));
        assertEquals(new StringFieldValue("bar"), result.get(1));
    }

    @Test
    public void requireThatNestedArraysAreTraversed() {
        StructDataType type = new StructDataType("matrix");
        ArrayDataType arrayOfArrayOfString = new ArrayDataType(new ArrayDataType(DataType.STRING));
        type.addField(new Field("rows", arrayOfArrayOfString));
        assertVerify(type, new ForEachFieldExpression("rows", new LowerCaseExpression()), type);

        Struct struct = new Struct(type);
        Array<Array<StringFieldValue>> rows = new Array<>(arrayOfArrayOfString);
        Array<StringFieldValue> row = new Array<>(arrayOfArrayOfString.getNestedType());
        row.add(new StringFieldValue("Foo"));
        row.add(new StringFieldValue("BAR"));
        rows.add(row);
        struct.setFieldValue("rows", rows);

        Struct output = (Struct)execute(new ForEachFieldExpression("rows", new LowerCaseExpression()), struct);
        Array<Array<StringFieldValue>> result = (Array<Array<StringFieldValue>>)output.getFieldValue("rows");
        assertEquals(arrayOfArrayOfString, result.getDataType());
        assertEquals(new StringFieldValue("foo"), result.get(0).get(0));
        assertEquals(new StringFieldValue("bar"), result.get(0).get(1));

        ArrayDataType arrayOfArrayOfNormal = new ArrayDataType(arrayOfNormal);
        assertVerify(arrayOfArrayOfNormal, new ForEachFieldExpression("myvalue", new LowerCaseExpression()), arrayOfArrayOfNormal);
        Array<Array<Struct>> outer = new Array<>(arrayOfArrayOfNormal);
        Array<Struct> inner = new Array<>(arrayOfNormal);
        inner.add(normal(1, "Foo"));
        outer.add(inner);
        Array<Array<Struct>> outerResult = (Array<Array<Struct>>)execute(new ForEachFieldExpression("myvalue", new LowerCaseExpression()), outer);
        assertEquals(normal(1, "foo"), outerResult.get(0).get(0));
    }

    @Test
    public void requireThatFieldsOfStructsInArrayMapValuesAreConverted() {
        MapDataType mapOfArrayOfNormal = new MapDataType(DataType.STRING, arrayOfNormal);
        assertVerify(mapOfArrayOfNormal, new ForEachFieldExpression("value.myvalue", new LowerCaseExpression()), mapOfArrayOfNormal);

        Array<Struct> structs = new Array<>(arrayOfNormal);
        structs.add(normal(1, "Foo"));
        structs.add(normal(2, "BAR"));
        MapFieldValue<StringFieldValue, Array<Struct>> input = new MapFieldValue<>(mapOfArrayOfNormal);
        input.put(new StringFieldValue("Key"), structs);

        MapFieldValue output = (MapFieldValue)execute(new ForEachFieldExpression("value.myvalue", new LowerCaseExpression()), input);
        assertEquals(mapOfArrayOfNormal, output.getDataType());
        Array<Struct> result = (Array<Struct>)output.get(new StringFieldValue("Key"));
        assertEquals(normal(1, "foo"), result.get(0));
        assertEquals(normal(2, "bar"), result.get(1));
    }

    @Test
    public void requireThatValueIsKeptWhenExpressionProducesNothing() {
        Array<Struct> input = new Array<>(arrayOfNormal);
        input.add(normal(1, "Foo"));

        // An unset variable produces null
        Array<Struct> output = (Array<Struct>)execute(new ForEachFieldExpression("myvalue", new GetVarExpression("unset")), input);
        assertEquals(normal(1, "Foo"), output.get(0));
    }

    @Test
    public void requireThatScriptIsParsedVerifiedAndExecuted() throws ParseException {
        var expression = Expression.fromString("input in | for_each_field \"myvalue\" { lowercase | tokenize } | summary out");

        SimpleTestAdapter adapter = new SimpleTestAdapter();
        adapter.createField(new Field("in", arrayOfNormal));
        adapter.createField(new Field("out", arrayOfNormal));
        Array<Struct> input = new Array<>(arrayOfNormal);
        input.add(normal(1, "Hello World"));
        adapter.setValue("in", input);

        expression.resolve(new TypeContext(adapter));
        expression.execute(new ExecutionContext(adapter));

        Array<Struct> output = (Array<Struct>)adapter.values.get("out");
        assertEquals(1, output.size());
        assertEquals(new IntegerFieldValue(1), output.get(0).getFieldValue("mykey"));
        StringFieldValue value = (StringFieldValue)output.get(0).getFieldValue("myvalue");
        assertEquals("hello world", value.getString());
        assertNotNull(value.getSpanTree(SpanTrees.LINGUISTICS));
    }

    @Test
    public void requireThatWeightedSetsAreRejected() {
        StructDataType type = new StructDataType("weighted");
        type.addField(new Field("tags", DataType.getWeightedSet(DataType.STRING)));
        type.addField(new Field("structs", DataType.getWeightedSet(normal)));
        assertVerifyThrows("Invalid expression 'for_each_field \"tags\" { lowercase }': Weighted sets are not supported, got WeightedSet<string>",
                           new ForEachFieldExpression("tags", new LowerCaseExpression()), type,
                           new TypeContext(new SimpleTestAdapter()));
        assertVerifyThrows("Invalid expression 'for_each_field \"structs.myvalue\" { lowercase }': Weighted sets are not supported, got WeightedSet<normal>",
                           new ForEachFieldExpression("structs.myvalue", new LowerCaseExpression()), type,
                           new TypeContext(new SimpleTestAdapter()));

        Struct struct = new Struct(type);
        WeightedSet<StringFieldValue> tags = new WeightedSet<>(type.getField("tags").getDataType());
        tags.put(new StringFieldValue("Foo"), 1);
        struct.setFieldValue("tags", tags);
        var e = assertThrows(IllegalArgumentException.class,
                             () -> execute(new ForEachFieldExpression("tags", new LowerCaseExpression()), struct));
        assertEquals("Weighted sets are not supported, got WeightedSet<string>", e.getMessage());
    }

    @Test
    public void requireThatMapKeyCollisionsAreRejected() {
        MapFieldValue<StringFieldValue, StringFieldValue> input = new MapFieldValue<>(mapOfString);
        input.put(new StringFieldValue("Foo"), new StringFieldValue("a"));
        input.put(new StringFieldValue("foo"), new StringFieldValue("b"));

        var e = assertThrows(IllegalArgumentException.class,
                             () -> execute(new ForEachFieldExpression("key", new LowerCaseExpression()), input));
        assertEquals("Map key 'foo' occurs more than once after executing 'lowercase' on the keys of a Map<string,string>",
                     e.getMessage());
        // Values may become equal
        MapFieldValue output = (MapFieldValue)execute(new ForEachFieldExpression("value", new ConstantExpression(new StringFieldValue("x"))), input);
        assertEquals(2, output.size());
    }

    @Test
    public void requireThatFieldsOfMapStructValuesAreConverted() {
        MapFieldValue<StringFieldValue, Struct> input = new MapFieldValue<>(mapOfNormal);
        input.put(new StringFieldValue("a"), normal(1, "Foo"));

        MapFieldValue output = (MapFieldValue)execute(new ForEachFieldExpression("value.myvalue", new LowerCaseExpression()), input);
        assertEquals(normal(1, "foo"), output.get(new StringFieldValue("a")));
    }

    @Test
    public void requireThatSelectedStringsAreAnnotated() {
        StructDataType type = new StructDataType("two");
        type.addField(new Field("a", DataType.STRING));
        type.addField(new Field("b", DataType.STRING));
        Struct struct = new Struct(type);
        struct.setFieldValue("a", new StringFieldValue("hello world"));
        struct.setFieldValue("b", new StringFieldValue("hello world"));
        Array<Struct> input = new Array<>(new ArrayDataType(type));
        input.add(struct);

        var tokenize = new TokenizeExpression(new SimpleLinguistics(), new AnnotatorConfig());
        Array<Struct> output = (Array<Struct>)execute(new ForEachFieldExpression("a", tokenize), input);

        Struct result = output.get(0);
        assertNotNull(((StringFieldValue)result.getFieldValue("a")).getSpanTree(SpanTrees.LINGUISTICS));
        assertNull(((StringFieldValue)result.getFieldValue("b")).getSpanTree(SpanTrees.LINGUISTICS));
        assertNull(((StringFieldValue)struct.getFieldValue("a")).getSpanTree(SpanTrees.LINGUISTICS));
    }

    @Test
    public void requireThatPathIsValidated() {
        for (String path : new String[] { "", ".", "a.", ".a", "a..b" }) {
            var e = assertThrows(IllegalArgumentException.class,
                                 () -> new ForEachFieldExpression(path, new LowerCaseExpression()));
            assertEquals("Invalid field path '" + path + "'", e.getMessage());
        }
    }

    private static FieldValue execute(Expression exp, FieldValue input) {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        ctx.setCurrentValue(input);
        exp.execute(ctx);
        return ctx.getCurrentValue();
    }

    private static Struct normal(int key, String value) {
        Struct struct = new Struct(normal);
        struct.setFieldValue("mykey", new IntegerFieldValue(key));
        if (value != null)
            struct.setFieldValue("myvalue", new StringFieldValue(value));
        return struct;
    }

}
