// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.*;
import com.yahoo.document.serialization.DocumentDeserializerFactory;
import com.yahoo.document.serialization.DocumentSerializer;
import com.yahoo.document.serialization.DocumentSerializerFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * @author Einar M R Rosenvinge
 */
public class FieldUpdateTestCase {

    private Field strfoo;
    private Field strarray;
    private Field strws;
    private Field strws2;
    private DocumentTypeManager docman = new DocumentTypeManager();

    @Before
    public void setUp() {
        docman = new DocumentTypeManager();

        DocumentType testType = new DocumentType("foobar");
        testType.addField(new Field("strfoo", DataType.STRING));
        testType.addField(new Field("strarray", DataType.getArray(DataType.STRING)));
        testType.addField(new Field("strws", DataType.getWeightedSet(DataType.STRING)));
        testType.addField(new Field("strws2", DataType.getWeightedSet(DataType.STRING, true, true)));
        docman.registerDocumentType(testType);


        strfoo = docman.getDocumentType("foobar").getField("strfoo");
        strarray = docman.getDocumentType("foobar").getField("strarray");
        strws = docman.getDocumentType("foobar").getField("strws");
        strws2 = docman.getDocumentType("foobar").getField("strws2");
    }

    @Test
    public void testInstantiationExceptions() {
        //add(field, value)
        try {
            FieldUpdate.createAdd(strfoo, new StringFieldValue("banana"));
            fail("Should have gotten exception");
        } catch (UnsupportedOperationException uoe) {}
        FieldUpdate.createAdd(strarray, new StringFieldValue("banana"));
        try {
            FieldUpdate.createAdd(strarray, new Array(DataType.getArray(DataType.STRING)));
                    fail("Should have gotten exception");
        } catch (IllegalArgumentException iae) {}
        FieldUpdate.createAdd(strws, new StringFieldValue("banana"));
        try {
            FieldUpdate.createAdd(strws, new Array(DataType.getArray(DataType.STRING)));
            fail("Should have gotten exception");
        } catch (IllegalArgumentException iae) {}


        //add(field, key, weight)
        try {
            FieldUpdate.createAdd(strfoo, new StringFieldValue("apple"), 5);
            fail("Should have gotten exception");
        } catch (UnsupportedOperationException uoe) {}
        FieldUpdate.createAdd(strarray, new StringFieldValue("apple"), 5);
        FieldUpdate.createAdd(strws, new StringFieldValue("apple"), 5);
        try {
            FieldUpdate.createAdd(strws, new Array(DataType.getArray(DataType.STRING)), 50);
            fail("Should have gotten exception");
        } catch (IllegalArgumentException iae) {}


        Array<StringFieldValue> fruitList = new Array<>(DataType.getArray(DataType.STRING));
        fruitList.add(new StringFieldValue("kiwi"));
        fruitList.add(new StringFieldValue("mango"));
        Array<Raw> rawList = new Array<>(DataType.getArray(DataType.RAW));
        rawList.add(new Raw());
        rawList.add(new Raw());
        rawList.add(new Raw());


        //addall(field, list)
        try {
            FieldUpdate.createAddAll(strfoo, fruitList);
            fail("Should have gotten exception");
        } catch (UnsupportedOperationException uoe) {}
        FieldUpdate.createAddAll(strarray, fruitList);
        try {
            FieldUpdate.createAddAll(strarray, rawList);
            fail("Should have gotten exception");
        } catch (IllegalArgumentException iae) {}
        FieldUpdate.createAddAll(strws, fruitList);

        WeightedSet fruitWs = new WeightedSet(DataType.getWeightedSet(DataType.STRING));
        fruitWs.put(new StringFieldValue("pineapple"), 50);
        fruitWs.put(new StringFieldValue("grape"), 10);
        WeightedSet rawWs = new WeightedSet(DataType.getWeightedSet(DataType.RAW));
        rawWs.put(new Raw(), 5);
        rawWs.put(new Raw(), 5);
        rawWs.put(new Raw(), 4);


        //addall(field, weightedset)
        try {
            FieldUpdate.createAddAll(strfoo, fruitWs);
            fail("Should have gotten exception");
        } catch (UnsupportedOperationException uoe) {}
        FieldUpdate.createAddAll(strarray, fruitWs);
        FieldUpdate.createAddAll(strws, fruitWs);
        try {
            FieldUpdate.createAddAll(strws, rawWs);
            fail("Should have gotten exception");
        } catch (IllegalArgumentException iae) {}


        //assign(field, object)
        FieldUpdate.createAssign(strfoo, new StringFieldValue("potato"));
        FieldUpdate.createAssign(strarray, fruitList);
        FieldUpdate.createAssign(strws, fruitWs);
        try {
            FieldUpdate.createAssign(strfoo, new IntegerFieldValue(69));
            fail("Should have gotten exception");
        } catch (IllegalArgumentException uoe) {}


        //decrement(field, object, decrement)
        try {
            FieldUpdate.createDecrement(strfoo, new StringFieldValue("ruccola"), 49d);
            fail("Should have gotten exception");
        } catch (UnsupportedOperationException uoe) {}
        try {
            FieldUpdate.createDecrement(strarray, new StringFieldValue("ruccola"), 49d);
            fail("Should have gotten exception");
        } catch (IllegalArgumentException iae) {}
        FieldUpdate.createDecrement(strws, new StringFieldValue("ruccola"), 49d);
        try {
            FieldUpdate.createDecrement(strws, new Raw(), 48d);
            fail("Should have gotten exception");
        } catch (IllegalArgumentException iae) {}


        //increment(field, object, increment)
        try {
            FieldUpdate.createIncrement(strfoo, new StringFieldValue("ruccola"), 49d);
            fail("Should have gotten exception");
        } catch (UnsupportedOperationException uoe) {}
        try {
            FieldUpdate.createIncrement(strarray, new StringFieldValue("ruccola"), 49d);
            fail("Should have gotten exception");
        } catch (IllegalArgumentException iae) {}
        FieldUpdate.createIncrement(strws, new StringFieldValue("ruccola"), 49d);
        try {
            FieldUpdate.createIncrement(strws, new Raw(), 48d);
            fail("Should have gotten exception");
        } catch (IllegalArgumentException iae) {}


        //remove(field)
        FieldUpdate.createClear(strfoo);
        FieldUpdate.createClear(strarray);
        FieldUpdate.createClear(strws);


        //remove(field, object)
        try {
            FieldUpdate.createRemove(strfoo, new StringFieldValue("salad"));
            fail("Should have gotten exception");
        } catch (UnsupportedOperationException uoe) {}
        FieldUpdate.createRemove(strarray, new StringFieldValue("salad"));
        try {
            FieldUpdate.createRemove(strarray, new Raw());
            fail("Should have gotten exception");
        } catch (IllegalArgumentException iae) {}
        FieldUpdate.createRemove(strws, new StringFieldValue("salad"));
        try {
            FieldUpdate.createRemove(strws, new Raw());
            fail("Should have gotten exception");
        } catch (IllegalArgumentException iae) {}
    }

    // Copy all field updates using serialization to verify that it is supported
    private FieldUpdate serializedCopy(FieldUpdate source, DocumentType docType) {
        DocumentSerializer buffer = DocumentSerializerFactory.create42();
        source.serialize(buffer);
        buffer.getBuf().flip();
        FieldUpdate copy = new FieldUpdate(DocumentDeserializerFactory.create42(docman, buffer.getBuf()), docType, Document.SERIALIZED_VERSION);
        assertEquals(source, copy);
        return copy;
    }

    @Test
    public void testApplyToSingleValue() {
        Document testDoc = new Document(docman.getDocumentType("foobar"), new DocumentId("doc:test:ballooo"));
        FieldUpdate alter = FieldUpdate.create(strfoo);

        ValueUpdate assign = ValueUpdate.createAssign(new StringFieldValue("potato"));
        alter.addValueUpdate(assign);
        alter = serializedCopy(alter, testDoc.getDataType());
        alter.applyTo(testDoc);
        assertEquals(new StringFieldValue("potato"), testDoc.getFieldValue("strfoo"));

        FieldUpdate clear = FieldUpdate.createClearField(strfoo);
        clear = serializedCopy(clear, testDoc.getDataType());
        clear.applyTo(testDoc);
        assertNull(testDoc.getFieldValue("strfoo"));
    }

    @Test
    public void testApplyToArray() {
        Array<StringFieldValue> fruitList = new Array<>(DataType.getArray(DataType.STRING));
        fruitList.add(new StringFieldValue("kiwi"));
        fruitList.add(new StringFieldValue("mango"));
        Document testDoc = new Document(docman.getDocumentType("foobar"), new DocumentId("doc:test:ballooo"));
        FieldUpdate alter = FieldUpdate.create(strarray);

        alter.addValueUpdate(ValueUpdate.createAdd(new StringFieldValue("banana")));
        alter = serializedCopy(alter, testDoc.getDataType());
        alter.applyTo(testDoc);
        assertEquals(1, ((List) testDoc.getFieldValue("strarray")).size());
        assertEquals(new StringFieldValue("banana"), ((List) testDoc.getFieldValue("strarray")).get(0));

        alter.clearValueUpdates();
        alter.addValueUpdates(ValueUpdate.createAddAll(fruitList));
        alter = serializedCopy(alter, testDoc.getDataType());
        alter.applyTo(testDoc);
        assertEquals(3, ((List) testDoc.getFieldValue("strarray")).size());
        assertEquals(new StringFieldValue("banana"), ((List) testDoc.getFieldValue("strarray")).get(0));
        assertEquals(new StringFieldValue("kiwi"), ((List) testDoc.getFieldValue("strarray")).get(1));
        assertEquals(new StringFieldValue("mango"), ((List) testDoc.getFieldValue("strarray")).get(2));

        alter.clearValueUpdates();
        alter.addValueUpdate(ValueUpdate.createAssign(fruitList));
        alter = serializedCopy(alter, testDoc.getDataType());
        alter.applyTo(testDoc);
        System.err.println(testDoc.getFieldValue("strarray"));
        assertEquals(2, ((List) testDoc.getFieldValue("strarray")).size());
        assertEquals(new StringFieldValue("kiwi"), ((List) testDoc.getFieldValue("strarray")).get(0));
        assertEquals(new StringFieldValue("mango"), ((List) testDoc.getFieldValue("strarray")).get(1));

        alter.clearValueUpdates();
        alter.addValueUpdate(ValueUpdate.createRemove(new StringFieldValue("kiwi")));
        alter = serializedCopy(alter, testDoc.getDataType());
        alter.applyTo(testDoc);
        assertEquals(1, ((List) testDoc.getFieldValue("strarray")).size());
        assertEquals(new StringFieldValue("mango"), ((List) testDoc.getFieldValue("strarray")).get(0));

        FieldUpdate clear = FieldUpdate.createClearField(strarray);
        clear = serializedCopy(clear, testDoc.getDataType());
        clear.applyTo(testDoc);
        assertNull(testDoc.getFieldValue("strarray"));
    }

    @Test
    public void testApplyToWs() {
        WeightedSet fruitWs = new WeightedSet(DataType.getWeightedSet(DataType.STRING));
        fruitWs.put(new StringFieldValue("pineapple"), 50);
        fruitWs.put(new StringFieldValue("apple"), 10);
        Document testDoc = new Document(docman.getDocumentType("foobar"), new DocumentId("doc:test:ballooo"));
        FieldUpdate alter = FieldUpdate.create(strws);
        FieldUpdate alter2 = FieldUpdate.create(strws2);


        alter.addValueUpdate(ValueUpdate.createAdd(new StringFieldValue("banana")));
        alter = serializedCopy(alter, testDoc.getDataType());
        alter.applyTo(testDoc);
        assertEquals(1, ((WeightedSet) testDoc.getFieldValue("strws")).size());
        assertEquals(1, (int) ((WeightedSet) testDoc.getFieldValue("strws")).get(new StringFieldValue("banana")));

        alter.clearValueUpdates();
        alter.addValueUpdate(ValueUpdate.createAdd(new StringFieldValue("apple"), 5));
        alter = serializedCopy(alter, testDoc.getDataType());
        alter.applyTo(testDoc);
        assertEquals(2, ((WeightedSet) testDoc.getFieldValue("strws")).size());
        assertEquals(1, (int) ((WeightedSet) testDoc.getFieldValue("strws")).get(new StringFieldValue("banana")));
        assertEquals(5, (int) ((WeightedSet) testDoc.getFieldValue("strws")).get(new StringFieldValue("apple")));

        alter.clearValueUpdates();
        alter.addValueUpdates(ValueUpdate.createAddAll(fruitWs));
        alter = serializedCopy(alter, testDoc.getDataType());
        alter.applyTo(testDoc);
        assertEquals(3, ((WeightedSet) testDoc.getFieldValue("strws")).size());
        assertEquals(1, (int) ((WeightedSet) testDoc.getFieldValue("strws")).get(new StringFieldValue("banana")));
        assertEquals(10, (int) ((WeightedSet) testDoc.getFieldValue("strws")).get(new StringFieldValue("apple")));
        assertEquals(50, (int) ((WeightedSet) testDoc.getFieldValue("strws")).get(new StringFieldValue("pineapple")));

        alter.clearValueUpdates();
        alter.addValueUpdate(ValueUpdate.createAssign(fruitWs));
        alter = serializedCopy(alter, testDoc.getDataType());
        alter.applyTo(testDoc);
        assertEquals(2, ((WeightedSet) testDoc.getFieldValue("strws")).size());
        assertEquals(50, (int) ((WeightedSet) testDoc.getFieldValue("strws")).get(new StringFieldValue("pineapple")));
        assertEquals(10, (int) ((WeightedSet) testDoc.getFieldValue("strws")).get(new StringFieldValue("apple")));

        alter.clearValueUpdates();
        alter.addValueUpdate(ValueUpdate.createDecrement(new StringFieldValue("pineapple"), 3d));
        alter = serializedCopy(alter, testDoc.getDataType());
        alter.applyTo(testDoc);
        assertEquals(2, ((WeightedSet) testDoc.getFieldValue("strws")).size());
        assertEquals(47, (int) ((WeightedSet) testDoc.getFieldValue("strws")).get(new StringFieldValue("pineapple")));
        assertEquals(10, (int) ((WeightedSet) testDoc.getFieldValue("strws")).get(new StringFieldValue("apple")));

        alter.clearValueUpdates();
        alter.addValueUpdate(ValueUpdate.createIncrement(new StringFieldValue("pineapple"), 13d));
        alter = serializedCopy(alter, testDoc.getDataType());
        alter.applyTo(testDoc);
        assertEquals(2, ((WeightedSet) testDoc.getFieldValue("strws")).size());
        assertEquals(60, (int) ((WeightedSet) testDoc.getFieldValue("strws")).get(new StringFieldValue("pineapple")));
        assertEquals(10, (int) ((WeightedSet) testDoc.getFieldValue("strws")).get(new StringFieldValue("apple")));

        alter.clearValueUpdates();
        alter.addValueUpdate(ValueUpdate.createRemove(new StringFieldValue("apple")));
        alter = serializedCopy(alter, testDoc.getDataType());
        alter.applyTo(testDoc);
        assertEquals(1, ((WeightedSet) testDoc.getFieldValue("strws")).size());
        assertEquals(60, (int) ((WeightedSet) testDoc.getFieldValue("strws")).get(new StringFieldValue("pineapple")));

        // Test createifnonexistant
        alter.clearValueUpdates();
        alter.addValueUpdate(ValueUpdate.createIncrement(new StringFieldValue("apple"), 1));
        alter = serializedCopy(alter, testDoc.getDataType());
        alter.applyTo(testDoc);
        assertNull(((WeightedSet)testDoc.getFieldValue("strws")).get(new StringFieldValue("apple")));

        alter2.addValueUpdate(ValueUpdate.createIncrement(new StringFieldValue("apple"), 1));
        alter2 = serializedCopy(alter2, testDoc.getDataType());
        alter2.applyTo(testDoc);
        assertEquals(1, ((WeightedSet) testDoc.getFieldValue("strws2")).size());
        assertEquals(1, (int) ((WeightedSet) testDoc.getFieldValue("strws2")).get(new StringFieldValue("apple")));

        // Test removeifzero
        alter.clearValueUpdates();
        alter.addValueUpdate(ValueUpdate.createAdd(new StringFieldValue("banana")));
        alter = serializedCopy(alter, testDoc.getDataType());
        alter.applyTo(testDoc);
        assertEquals(1, (int) ((WeightedSet)testDoc.getFieldValue("strws")).get(new StringFieldValue("banana")));
        alter.clearValueUpdates();
        alter.addValueUpdate(ValueUpdate.createDecrement(new StringFieldValue("banana"), 1));
        alter = serializedCopy(alter, testDoc.getDataType());
        alter.applyTo(testDoc);
        assertEquals(0, (int) ((WeightedSet)testDoc.getFieldValue("strws")).get(new StringFieldValue("banana")));

        alter2.clearValueUpdates();
        alter2.addValueUpdate(ValueUpdate.createAdd(new StringFieldValue("banana")));
        alter2 = serializedCopy(alter2, testDoc.getDataType());
        alter2.applyTo(testDoc);
        assertEquals(1, (int) ((WeightedSet)testDoc.getFieldValue("strws2")).get(new StringFieldValue("banana")));

        alter2.clearValueUpdates();
        alter2.addValueUpdate(ValueUpdate.createDecrement(new StringFieldValue("banana"), 1));
        alter2 = serializedCopy(alter2, testDoc.getDataType());
        alter2.applyTo(testDoc);
        assertNull(((WeightedSet)testDoc.getFieldValue("strws2")).get(new StringFieldValue("banana")));

        FieldUpdate clear = FieldUpdate.createClearField(strws);
        clear = serializedCopy(clear, testDoc.getDataType());
        clear.applyTo(testDoc);
        assertNull(testDoc.getFieldValue("strws"));
    }

    @Test
    public void testArithmeticUpdatesOnAutoCreatedWSetItemsAreZeroBased() {
        Document testDoc = new Document(
                docman.getDocumentType("foobar"),
                new DocumentId("doc:test:ballooo"));
        // strws2 is fixture weightedset type with create-if-non-existing
        // and remove-if-zero attributes set.
        FieldUpdate update = FieldUpdate.create(strws2);
        StringFieldValue key = new StringFieldValue("apple");
        update.addValueUpdate(ValueUpdate.createIncrement(key, 1));
        update.applyTo(testDoc);
        assertEquals(1, ((WeightedSet)testDoc.getFieldValue("strws2")).size());
        assertEquals(1, (int)((WeightedSet) testDoc.getFieldValue("strws2")).get(key));
    }

}
