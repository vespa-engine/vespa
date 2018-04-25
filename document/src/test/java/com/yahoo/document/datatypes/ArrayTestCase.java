// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Einar M R Rosenvinge
 */
public class ArrayTestCase {

    @Test
    public void testToArray() {
        ArrayDataType dt = new ArrayDataType(DataType.STRING);
        Array<StringFieldValue> arr = new Array<>(dt);
        arr.add(new StringFieldValue("a"));
        arr.add(new StringFieldValue("b"));
        arr.add(new StringFieldValue("c"));
        StringFieldValue[] tooSmall = new StringFieldValue[0];
        StringFieldValue[] bigEnough = new StringFieldValue[3];
        StringFieldValue[] a = arr.toArray(tooSmall);
        assertNotSame(tooSmall, a);
        assertEquals(new StringFieldValue("a"), a[0]);
        assertEquals(new StringFieldValue("b"), a[1]);
        assertEquals(new StringFieldValue("c"), a[2]);
        StringFieldValue[] b = arr.toArray(bigEnough);
        assertSame(bigEnough, b);
        assertEquals(new StringFieldValue("a"), b[0]);
        assertEquals(new StringFieldValue("b"), b[1]);
        assertEquals(new StringFieldValue("c"), b[2]);
    }

    @Test
    public void testCreateIllegalArray() {
        ArrayList<FieldValue> arrayList = new ArrayList<>();
        arrayList.add(new StringFieldValue("foo"));
        arrayList.add(new IntegerFieldValue(1000));
        DataType stringType = new ArrayDataType(DataType.STRING);
        try {
            Array<FieldValue> illegalArray = new Array<>(stringType, arrayList);
            fail("Expected an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("FieldValue 1000 is not compatible with datatype "
                         + "Array<string> (code: -1486737430).",
                         e.getMessage());
        }

        DataType intType = new ArrayDataType(DataType.INT);
        Array<IntegerFieldValue> intArray = new Array<>(intType);
        intArray.add(new IntegerFieldValue(42));
        Array<StringFieldValue> stringArray = new Array<>(stringType);
        try {
            stringArray.assign(intArray);
            fail("Expected an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Incompatible data types. Got datatype int (code: 0),"
                         + " expected datatype string (code: 2)",
                         e.getMessage());
        }
    }

    @Test
    public void testWrappedList() {
        Array<StringFieldValue> array = new Array<StringFieldValue>(DataType.getArray(DataType.STRING));
        List<String> list = new ArrayList<>();
        list.add("foo");
        list.add("bar");
        list.add("baz");

        array.assign(list);

        assertEquals(3, array.size());
        assertEquals(3, list.size());
        assertFalse(array.isEmpty());
        assertFalse(list.isEmpty());
        assertEquals("foo", list.get(0));
        assertEquals("bar", list.get(1));
        assertEquals("baz", list.get(2));
        assertEquals(new StringFieldValue("foo"), array.get(0));
        assertEquals(new StringFieldValue("bar"), array.get(1));
        assertEquals(new StringFieldValue("baz"), array.get(2));

        array.remove(2);

        assertEquals(2, array.size());
        assertEquals(2, list.size());
        assertEquals("foo", list.get(0));
        assertEquals("bar", list.get(1));
        assertEquals(new StringFieldValue("foo"), array.get(0));
        assertEquals(new StringFieldValue("bar"), array.get(1));

        list.remove(1);

        assertEquals(1, array.size());
        assertEquals(1, list.size());
        assertEquals("foo", list.get(0));
        assertEquals(new StringFieldValue("foo"), array.get(0));


        list.add("bar");

        assertEquals(2, array.size());
        assertEquals(2, list.size());
        assertEquals("foo", list.get(0));
        assertEquals("bar", list.get(1));
        assertEquals(new StringFieldValue("foo"), array.get(0));
        assertEquals(new StringFieldValue("bar"), array.get(1));

        array.add(new StringFieldValue("baz"));

        assertEquals(3, array.size());
        assertEquals(3, list.size());
        assertEquals("foo", list.get(0));
        assertEquals("bar", list.get(1));
        assertEquals("baz", list.get(2));
        assertEquals(new StringFieldValue("foo"), array.get(0));
        assertEquals(new StringFieldValue("bar"), array.get(1));
        assertEquals(new StringFieldValue("baz"), array.get(2));

        assertTrue(array.contains(new StringFieldValue("foo")));
        assertTrue(list.contains("foo"));

        list.add("foo");

        assertEquals(0, list.indexOf("foo"));
        assertEquals(0, array.indexOf(new StringFieldValue("foo")));
        assertEquals(3, list.lastIndexOf("foo"));
        assertEquals(3, array.lastIndexOf(new StringFieldValue("foo")));

        list.set(3, "banana");

        assertEquals(4, array.size());
        assertEquals(4, list.size());
        assertEquals("foo", list.get(0));
        assertEquals("bar", list.get(1));
        assertEquals("baz", list.get(2));
        assertEquals("banana", list.get(3));
        assertEquals(new StringFieldValue("foo"), array.get(0));
        assertEquals(new StringFieldValue("bar"), array.get(1));
        assertEquals(new StringFieldValue("baz"), array.get(2));
        assertEquals(new StringFieldValue("banana"), array.get(3));

        array.set(3, new StringFieldValue("apple"));

        assertEquals(4, array.size());
        assertEquals(4, list.size());
        assertEquals("foo", list.get(0));
        assertEquals("bar", list.get(1));
        assertEquals("baz", list.get(2));
        assertEquals("apple", list.get(3));
        assertEquals(new StringFieldValue("foo"), array.get(0));
        assertEquals(new StringFieldValue("bar"), array.get(1));
        assertEquals(new StringFieldValue("baz"), array.get(2));
        assertEquals(new StringFieldValue("apple"), array.get(3));

        list.remove("bar");

        assertEquals(3, array.size());
        assertEquals(3, list.size());
        assertEquals("foo", list.get(0));
        assertEquals("baz", list.get(1));
        assertEquals("apple", list.get(2));
        assertEquals(new StringFieldValue("foo"), array.get(0));
        assertEquals(new StringFieldValue("baz"), array.get(1));
        assertEquals(new StringFieldValue("apple"), array.get(2));

        array.remove(new StringFieldValue("baz"));

        assertEquals(2, array.size());
        assertEquals(2, list.size());
        assertEquals("foo", list.get(0));
        assertEquals("apple", list.get(1));
        assertEquals(new StringFieldValue("foo"), array.get(0));
        assertEquals(new StringFieldValue("apple"), array.get(1));

        assertNotNull(array.toArray(new StringFieldValue[5]));

        try {
            array.retainAll(new ArrayList<StringFieldValue>());
            fail("Not implemented yet.");
        } catch (UnsupportedOperationException uoe) {
            //ok!
        }

        array.add(1, new StringFieldValue("boo"));

        assertEquals(3, array.size());
        assertEquals(3, list.size());
        assertEquals("foo", list.get(0));
        assertEquals("boo", list.get(1));
        assertEquals("apple", list.get(2));
        assertEquals(new StringFieldValue("foo"), array.get(0));
        assertEquals(new StringFieldValue("boo"), array.get(1));
        assertEquals(new StringFieldValue("apple"), array.get(2));

        array.toString();

        List<StringFieldValue> subArray = array.subList(1, 3);
        assertEquals(2, subArray.size());
        assertEquals(new StringFieldValue("boo"), subArray.get(0));
        assertEquals(new StringFieldValue("apple"), subArray.get(1));


        assertEquals(false, array.containsAll(Arrays.<StringFieldValue>asList(new StringFieldValue("bob"))));
        assertEquals(true, array.containsAll(Arrays.<StringFieldValue>asList(new StringFieldValue("foo"), new StringFieldValue("boo"), new StringFieldValue("apple"))));

        array.removeAll(Arrays.<StringFieldValue>asList(new StringFieldValue("foo"), new StringFieldValue("boo")));

        assertEquals(1, array.size());
        assertEquals(1, list.size());
        assertEquals("apple", list.get(0));
        assertEquals(new StringFieldValue("apple"), array.get(0));

        array.add(new StringFieldValue("ibm"));

        assertEquals(2, array.size());
        assertEquals(2, list.size());

        {
            Iterator<StringFieldValue> it = array.iterator();
            assertTrue(it.hasNext());
            assertEquals(new StringFieldValue("apple"), it.next());
            assertTrue(it.hasNext());
            assertEquals(new StringFieldValue("ibm"), it.next());
            assertFalse(it.hasNext());
        }
        {
            ListIterator<StringFieldValue> it = array.listIterator();
            assertTrue(it.hasNext());
            assertEquals(new StringFieldValue("apple"), it.next());
            assertTrue(it.hasNext());
            assertEquals(new StringFieldValue("ibm"), it.next());
            assertFalse(it.hasNext());
        }

        array.addAll(Arrays.<StringFieldValue>asList(new StringFieldValue("microsoft"), new StringFieldValue("google")));

        assertEquals(4, array.size());
        assertEquals(4, list.size());

        array.clear();

        assertEquals(0, array.size());
        assertEquals(0, list.size());

    }

    @Test
    public void testListWrapperToArray() {
        Array<StringFieldValue> array = new Array<>(new ArrayDataType(DataType.STRING));
        List<StringFieldValue> assignFrom = new ArrayList<>(3);
        assignFrom.add(new StringFieldValue("a"));
        assignFrom.add(new StringFieldValue("b"));
        assignFrom.add(new StringFieldValue("c"));
        array.assign(assignFrom);
        final StringFieldValue[] expected = new StringFieldValue[] { new StringFieldValue("a"), new StringFieldValue("b"),
                new StringFieldValue("c") };
        assertTrue(Arrays.equals(expected, array.toArray(new StringFieldValue[0])));
    }

}
