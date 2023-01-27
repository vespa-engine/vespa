// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.*;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlStream;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings({ "rawtypes" })
public class FieldValueConverterTestCase {

    @Test
    public void requireThatNullIsIgnored() {
        assertNull(new FieldValueConverter() {

            @Override
            protected boolean shouldConvert(FieldValue value) {
                throw new AssertionError();
            }

            @Override
            protected FieldValue doConvert(FieldValue value) {
                throw new AssertionError();
            }
        }.convert(null));
    }

    @Test
    public void requireThatUnknownTypesFallThrough() {
        FieldValue val = new MyFieldValue();
        assertSame(val, new FieldValueConverter() {

            @Override
            protected boolean shouldConvert(FieldValue value) {
                return false;
            }

            @Override
            protected FieldValue doConvert(FieldValue value) {
                throw new AssertionError();
            }
        }.convert(val));
    }

    @Test
    public void requireThatSingleValueIsConverted() {
        StringFieldValue before = new StringFieldValue("69");
        FieldValue after = new StringMarker().doConvert(before);

        assertTrue(after instanceof StringFieldValue);
        assertEquals(new StringFieldValue("69'"), after);
    }

    @Test
    public void requireThatArrayElementsAreConverted() {
        Array<StringFieldValue> before = new Array<>(DataType.getArray(DataType.STRING));
        before.add(new StringFieldValue("6"));
        before.add(new StringFieldValue("9"));
        FieldValue after = new StringMarker().convert(before);

        assertTrue(after instanceof Array);
        assertEquals(new StringFieldValue("6'"), ((Array)after).get(0));
        assertEquals(new StringFieldValue("9'"), ((Array)after).get(1));
    }

    @Test
    public void requireThatConvertedArrayElementCompatibilityIsEnforced() {
        Array<StringFieldValue> arr = new Array<>(DataType.getArray(DataType.STRING));
        StringFieldValue foo = new StringFieldValue("foo");
        StringFieldValue bar = new StringFieldValue("bar");
        arr.add(foo);
        arr.add(bar);
        try {
            new SearchReplace(foo, new IntegerFieldValue(69)).convert(arr);
            fail();
        } catch (IllegalArgumentException e) {

        }
        try {
            new SearchReplace(bar, new IntegerFieldValue(69)).convert(arr);
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    @Test
    public void requireThatMapEntriesAreConverted() {
        MapFieldValue<StringFieldValue, StringFieldValue> before =
                new MapFieldValue<>(DataType.getMap(DataType.STRING, DataType.STRING));
        before.put(new StringFieldValue("6"), new StringFieldValue("9"));
        before.put(new StringFieldValue("9"), new StringFieldValue("6"));
        FieldValue after = new StringMarker().convert(before);

        assertTrue(after instanceof MapFieldValue);
        assertEquals(new StringFieldValue("6'"), ((MapFieldValue)after).get(new StringFieldValue("9'")));
        assertEquals(new StringFieldValue("9'"), ((MapFieldValue)after).get(new StringFieldValue("6'")));
    }

    @Test
    public void requireThatMapElementsCanBeRemoved() {
        MapFieldValue<StringFieldValue, StringFieldValue> before =
                new MapFieldValue<>(DataType.getMap(DataType.STRING, DataType.STRING));
        StringFieldValue foo = new StringFieldValue("foo");
        StringFieldValue bar = new StringFieldValue("bar");
        StringFieldValue baz = new StringFieldValue("baz");
        StringFieldValue cox = new StringFieldValue("cox");
        before.put(foo, bar);
        before.put(baz, cox);

        FieldValue after = new SearchReplace(foo, null).convert(before);
        assertTrue(after instanceof MapFieldValue);
        assertEquals(1, ((MapFieldValue)after).size());

        after = new SearchReplace(bar, null).convert(before);
        assertTrue(after instanceof MapFieldValue);
        assertEquals(1, ((MapFieldValue)after).size());

        after = new SearchReplace(baz, null).convert(before);
        assertTrue(after instanceof MapFieldValue);
        assertEquals(1, ((MapFieldValue)after).size());

        after = new SearchReplace(cox, null).convert(before);
        assertTrue(after instanceof MapFieldValue);
        assertEquals(1, ((MapFieldValue)after).size());
    }

    @Test
    public void requireThatConvertedMapElementCompatibilityIsEnforced() {
        MapFieldValue<StringFieldValue, StringFieldValue> before =
                new MapFieldValue<>(DataType.getMap(DataType.STRING, DataType.STRING));
        StringFieldValue foo = new StringFieldValue("foo");
        StringFieldValue bar = new StringFieldValue("bar");
        StringFieldValue baz = new StringFieldValue("baz");
        StringFieldValue cox = new StringFieldValue("cox");
        before.put(foo, bar);
        before.put(baz, cox);

        try {
            new SearchReplace(foo, new IntegerFieldValue(69)).convert(before);
            fail();
        } catch (IllegalArgumentException e) {

        }
        try {
            new SearchReplace(bar, new IntegerFieldValue(69)).convert(before);
            fail();
        } catch (IllegalArgumentException e) {

        }
        try {
            new SearchReplace(baz, new IntegerFieldValue(69)).convert(before);
            fail();
        } catch (IllegalArgumentException e) {

        }
        try {
            new SearchReplace(cox, new IntegerFieldValue(69)).convert(before);
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    @Test
    public void requireThatWsetElementsAreConverted() {
        WeightedSet<StringFieldValue> before = new WeightedSet<>(DataType.getWeightedSet(DataType.STRING));
        before.put(new StringFieldValue("6"), 69);
        before.put(new StringFieldValue("9"), 96);
        FieldValue after = new StringMarker().convert(before);

        assertTrue(after instanceof WeightedSet);
        @SuppressWarnings("unchecked")
        WeightedSet<StringFieldValue> w = (WeightedSet<StringFieldValue>) after;
        assertEquals(2, w.size());
        assertTrue(w.contains(new StringFieldValue("9'")));
        assertEquals(96, w.get(new StringFieldValue("9'")).intValue());
        assertTrue(w.contains(new StringFieldValue("6'")));
        assertEquals(69, w.get(new StringFieldValue("6'")).intValue());
    }

    @Test
    public void requireThatWsetElementsCanBeRemoved() {
        WeightedSet<StringFieldValue> before = new WeightedSet<>(DataType.getWeightedSet(DataType.STRING));
        StringFieldValue foo = new StringFieldValue("foo");
        StringFieldValue bar = new StringFieldValue("bar");
        before.put(foo, 6);
        before.put(bar, 9);

        FieldValue after = new SearchReplace(foo, null).convert(before);
        assertTrue(after instanceof WeightedSet);
        assertEquals(1, ((WeightedSet)after).size());

        after = new SearchReplace(bar, null).convert(before);
        assertTrue(after instanceof WeightedSet);
        assertEquals(1, ((WeightedSet)after).size());
    }

    @Test
    public void requireThatConvertedWsetElementCompatibilityIsEnforced() {
        WeightedSet<StringFieldValue> before = new WeightedSet<>(DataType.getWeightedSet(DataType.STRING));
        StringFieldValue foo = new StringFieldValue("foo");
        StringFieldValue bar = new StringFieldValue("bar");
        before.put(foo, 6);
        before.put(bar, 9);

        try {
            new SearchReplace(foo, new IntegerFieldValue(69)).convert(before);
            fail();
        } catch (IllegalArgumentException e) {

        }
        try {
            new SearchReplace(bar, new IntegerFieldValue(69)).convert(before);
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    @Test
    public void requireThatEmptyCollectionsAreConvertedToNull() {
        FieldValueConverter converter = new FieldValueConverter() {

            @Override
            protected boolean shouldConvert(FieldValue value) {
                return false;
            }

            @Override
            protected FieldValue doConvert(FieldValue value) {
                throw new AssertionError();
            }
        };
        assertNull(converter.convert(new Array(DataType.getArray(DataType.STRING))));
        assertNull(converter.convert(new MapFieldValue(DataType.getMap(DataType.STRING, DataType.STRING))));
        assertNull(converter.convert(new WeightedSet<StringFieldValue>(DataType.getWeightedSet(DataType.STRING))));
    }

    @Test
    public void requireThatStructElementsAreConverted() {
        StructDataType type = new StructDataType("foo");
        type.addField(new Field("bar", DataType.STRING));
        type.addField(new Field("baz", DataType.STRING));
        Struct before = type.createFieldValue();
        before.setFieldValue("bar", new StringFieldValue("6"));
        before.setFieldValue("baz", new StringFieldValue("9"));
        FieldValue after = new StringMarker().convert(before);

        assertTrue(after instanceof Struct);
        assertEquals(new StringFieldValue("6'"), ((Struct)after).getFieldValue("bar"));
        assertEquals(new StringFieldValue("9'"), ((Struct)after).getFieldValue("baz"));
    }

    @Test
    public void requireThatStructElementsCanBeRemoved() {
        StructDataType type = new StructDataType("foo");
        type.addField(new Field("bar", DataType.STRING));
        type.addField(new Field("baz", DataType.STRING));
        Struct before = type.createFieldValue();
        StringFieldValue barVal = new StringFieldValue("6");
        StringFieldValue bazVal = new StringFieldValue("9");
        before.setFieldValue("bar", barVal);
        before.setFieldValue("baz", bazVal);

        FieldValue after = new SearchReplace(barVal, null).convert(before);
        assertTrue(after instanceof Struct);
        assertNull(((Struct)after).getFieldValue("bar"));
        assertEquals(bazVal, ((Struct)after).getFieldValue("baz"));

        after = new SearchReplace(bazVal, null).convert(before);
        assertTrue(after instanceof Struct);
        assertEquals(barVal, ((Struct)after).getFieldValue("bar"));
        assertNull(((Struct)after).getFieldValue("baz"));
    }

    @Test
    public void requireThatStructArrayElementsAreConverted() {
        StructDataType type = new StructDataType("foo");
        type.addField(new Field("bar", DataType.STRING));
        type.addField(new Field("baz", DataType.STRING));
        Array<Struct> before = new Array<>(DataType.getArray(type));
        Struct elem = type.createFieldValue();
        elem.setFieldValue("bar", new StringFieldValue("6"));
        elem.setFieldValue("baz", new StringFieldValue("9"));
        before.add(elem);
        elem = type.createFieldValue();
        elem.setFieldValue("bar", new StringFieldValue("9"));
        elem.setFieldValue("baz", new StringFieldValue("6"));
        before.add(elem);
        FieldValue after = new StringMarker().convert(before);

        assertTrue(after instanceof Array);
        FieldValue val = ((Array)after).getFieldValue(0);
        assertTrue(val instanceof Struct);
        assertEquals(new StringFieldValue("6'"), ((Struct)val).getFieldValue("bar"));
        assertEquals(new StringFieldValue("9'"), ((Struct)val).getFieldValue("baz"));
        val = ((Array)after).getFieldValue(1);
        assertTrue(val instanceof Struct);
        assertEquals(new StringFieldValue("9'"), ((Struct)val).getFieldValue("bar"));
        assertEquals(new StringFieldValue("6'"), ((Struct)val).getFieldValue("baz"));
    }

    @Test
    public void requireThatArrayChangeNestedType() {
        Array<StringFieldValue> before = new Array<>(DataType.getArray(DataType.STRING));
        before.add(new StringFieldValue("69"));
        FieldValue after = new IntegerParser().convert(before);

        assertTrue(after instanceof Array);
        assertEquals(DataType.getArray(DataType.INT), after.getDataType());
    }

    @Test
    public void requireThatMapChangeNestedType() {
        MapFieldValue<StringFieldValue, StringFieldValue> before =
                new MapFieldValue<>(
                        DataType.getMap(DataType.STRING, DataType.STRING));
        before.put(new StringFieldValue("6"), new StringFieldValue("9"));
        FieldValue after = new IntegerParser().convert(before);

        assertTrue(after instanceof MapFieldValue);
        assertEquals(DataType.getMap(DataType.INT, DataType.INT), after.getDataType());
    }

    @Test
    public void requireThatWsetChangeNestedType() {
        assertWsetChangeNestedType(false, false);
        assertWsetChangeNestedType(false, true);
        assertWsetChangeNestedType(true, false);
        assertWsetChangeNestedType(true, true);
    }

    private static void assertWsetChangeNestedType(boolean createIfNonExistent, boolean removeIfZero) {
        WeightedSet<StringFieldValue> before =
                new WeightedSet<>(DataType.getWeightedSet(DataType.STRING, createIfNonExistent, removeIfZero));
        before.put(new StringFieldValue("6"), 9);
        FieldValue after = new IntegerParser().convert(before);

        assertTrue(after instanceof WeightedSet);
        assertEquals(DataType.getWeightedSet(DataType.INT, createIfNonExistent, removeIfZero), after.getDataType());
    }

    private static class StringMarker extends StringFieldConverter {

        @Override
        protected FieldValue doConvert(StringFieldValue value) {
            return new StringFieldValue(value.toString() + "'");
        }
    }

    private static class IntegerParser extends StringFieldConverter {

        @Override
        protected FieldValue doConvert(StringFieldValue value) {
            return new IntegerFieldValue(Integer.valueOf(value.toString()));
        }
    }

    private static class SearchReplace extends FieldValueConverter {

        final FieldValue searchFor;
        final FieldValue replaceWith;

        private SearchReplace(FieldValue searchFor, FieldValue replaceWith) {
            this.searchFor = searchFor;
            this.replaceWith = replaceWith;
        }

        @Override
        protected boolean shouldConvert(FieldValue value) {
            return value == searchFor;
        }

        @Override
        protected FieldValue doConvert(FieldValue value) {
            return replaceWith;
        }
    }

    private static class MyFieldValue extends FieldValue {

        @Override
        public DataType getDataType() {
            return null;
        }

        @Override
        @Deprecated
        public void printXml(XmlStream xml) {

        }

        @Override
        public void clear() {

        }

        @Override
        public void assign(Object obj) {

        }

        @Override
        public void serialize(Field field, FieldWriter writer) {

        }

        @Override
        public void deserialize(Field field, FieldReader reader) {

        }
    }
}
