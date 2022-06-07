// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import com.yahoo.document.serialization.DocumentDeserializer;
import com.yahoo.document.serialization.DocumentDeserializerFactory;
import com.yahoo.document.serialization.DocumentSerializer;
import com.yahoo.document.serialization.DocumentSerializerFactory;
import com.yahoo.document.serialization.DocumentUpdateFlags;
import com.yahoo.document.update.AssignValueUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.TensorAddUpdate;
import com.yahoo.document.update.TensorModifyUpdate;
import com.yahoo.document.update.TensorRemoveUpdate;
import com.yahoo.document.update.ValueUpdate;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests applying and serializing document updates.
 *
 * @author Einar M R Rosenvinge
 */
public class DocumentUpdateTestCase {

    private DocumentTypeManager docMan;

    private DocumentType docType = null;
    private DocumentType docType2 = null;
    private DocumentUpdate docUp = null;

    private FieldUpdate assignSingle = null;
    private FieldUpdate assignMultiList = null;
    private FieldUpdate assignMultiWset = null;

    private FieldUpdate clearSingleField = null;
    private FieldUpdate removeMultiList = null;
    private FieldUpdate removeMultiWset = null;

    private FieldUpdate addSingle = null;
    private FieldUpdate addMultiList = null;
    private FieldUpdate addMultiWset = null;

    private final String documentId = "id:ns:foobar::foooo";
    private final String tensorField = "tensorfield";
    private final TensorType tensorType = new TensorType.Builder().mapped("x").build();

    private Document createDocument() {
        return new Document(docMan.getDocumentType("foobar"), new DocumentId(documentId));
    }

    @Before
    public void setUp() {
        docMan = new DocumentTypeManager();

        docType = new DocumentType("foobar");
        docType.addField(new Field("strfoo", DataType.STRING));

        DataType stringarray = DataType.getArray(DataType.STRING);
        docType.addField(new Field("strarray", stringarray));

        DataType stringwset = DataType.getWeightedSet(DataType.STRING);
        docType.addField(new Field("strwset", stringwset));

        docType.addField(new Field(tensorField, new TensorDataType(tensorType)));
        docMan.register(docType);

        docType2 = new DocumentType("otherdoctype");
        docType2.addField(new Field("strinother", DataType.STRING));
        docMan.register(docType2);

        docUp = new DocumentUpdate(docType, new DocumentId("id:ns:foobar::bar"));

        assignSingle = FieldUpdate.createAssign(docType.getField("strfoo"), new StringFieldValue("something"));

        Array<StringFieldValue> assignList = new Array<>(docType.getField("strarray").getDataType());
        assignList.add(new StringFieldValue("assigned val 0"));
        assignList.add(new StringFieldValue("assigned val 1"));
        assignMultiList = FieldUpdate.createAssign(docType.getField("strarray"), assignList);

        WeightedSet<StringFieldValue> assignWset = new WeightedSet<>(docType.getField("strwset").getDataType());
        assignWset.put(new StringFieldValue("assigned val 0"), 5);
        assignWset.put(new StringFieldValue("assigned val 1"), 10);
        assignMultiWset = FieldUpdate.createAssign(docType.getField("strwset"), assignWset);

        clearSingleField = FieldUpdate.createClearField(docType.getField("strfoo"));

        removeMultiList = FieldUpdate.createRemove(docType.getField("strarray"), new StringFieldValue("remove val 1"));

        removeMultiWset = FieldUpdate.createRemove(docType.getField("strwset"), new StringFieldValue("remove val 1"));

        try {
            addSingle = FieldUpdate.createAdd(docType.getField("strfoo"), new StringFieldValue("add val"));
            fail("Shouldn't be able to add to a single-value field");
        } catch (UnsupportedOperationException uoe) {
            //success
        }

        List<StringFieldValue> addList = new ArrayList<>();
        addList.add(new StringFieldValue("bo"));
        addList.add(new StringFieldValue("ba"));
        addList.add(new StringFieldValue("by"));
        addMultiList = FieldUpdate.createAddAll(docType.getField("strarray"), addList);

        WeightedSet<StringFieldValue> addSet = new WeightedSet<StringFieldValue>(docType.getField("strwset").getDataType());
        addSet.put(new StringFieldValue("banana"), 137);
        addSet.put(new StringFieldValue("is"), 143);
        addSet.put(new StringFieldValue("a"), 157);
        addSet.put(new StringFieldValue("great"), 163);
        addSet.put(new StringFieldValue("fruit"), 189);
        addMultiWset = FieldUpdate.createAddAll(docType.getField("strwset"), addSet);
    }

    @Test
    public void testApplyRemoveSingle() {
        Document doc = createDocument();
        assertNull(doc.getFieldValue("strfoo"));
        doc.setFieldValue("strfoo", new StringFieldValue("cocacola"));
        assertEquals(new StringFieldValue("cocacola"), doc.getFieldValue("strfoo"));
        docUp.addFieldUpdate(clearSingleField);
        docUp.applyTo(doc);
        assertNull(doc.getFieldValue("strfoo"));
    }

    @Test
    public void testApplyRemoveMultiList() {
        Document doc = createDocument();
        assertNull(doc.getFieldValue("strarray"));
        Array<StringFieldValue> strArray = new Array<>(DataType.getArray(DataType.STRING));
        strArray.add(new StringFieldValue("hello hello"));
        strArray.add(new StringFieldValue("remove val 1"));
        doc.setFieldValue("strarray", strArray);
        assertNotNull(doc.getFieldValue("strarray"));
        docUp.addFieldUpdate(removeMultiList);
        docUp.applyTo(doc);
        assertEquals(1, ((List)doc.getFieldValue("strarray")).size());
        List docList = (List)doc.getFieldValue("strarray");
        assertEquals(new StringFieldValue("hello hello"), docList.get(0));
    }

    @Test
    public void testApplyRemoveMultiWset() {
        Document doc = createDocument();
        assertNull(doc.getFieldValue("strwset"));
        WeightedSet<StringFieldValue> strwset = new WeightedSet<>(doc.getDataType().getField("strwset").getDataType());
        strwset.put(new StringFieldValue("hello hello"), 10);
        strwset.put(new StringFieldValue("remove val 1"), 20);
        doc.setFieldValue("strwset", strwset);
        assertNotNull(doc.getFieldValue("strwset"));
        docUp.addFieldUpdate(removeMultiWset);
        docUp.applyTo(doc);
        assertEquals(1, ((WeightedSet)doc.getFieldValue("strwset")).size());
        WeightedSet docWset = (WeightedSet)doc.getFieldValue("strwset");
        assertEquals(Integer.valueOf(10), docWset.get(new StringFieldValue("hello hello")));
    }

    @Test
    public void testApplyAssignSingle() {
        Document doc = createDocument();
        assertNull(doc.getFieldValue("strfoo"));
        docUp.addFieldUpdate(assignSingle);
        docUp.applyTo(doc);
        assertEquals(new StringFieldValue("something"), doc.getFieldValue("strfoo"));
    }

    @Test
    public void testApplyAssignMultiList() {
        Document doc = createDocument();
        assertNull(doc.getFieldValue("strarray"));
        Array<StringFieldValue> strArray = new Array<>(DataType.getArray(DataType.STRING));
        strArray.add(new StringFieldValue("hello hello"));
        strArray.add(new StringFieldValue("blah blah"));
        doc.setFieldValue("strarray", strArray);
        assertNotNull(doc.getFieldValue("strarray"));
        docUp.addFieldUpdate(assignMultiList);
        docUp.applyTo(doc);
        assertEquals(2, ((List)doc.getFieldValue("strarray")).size());
        List docList = (List)doc.getFieldValue("strarray");
        assertEquals(new StringFieldValue("assigned val 0"), docList.get(0));
        assertEquals(new StringFieldValue("assigned val 1"), docList.get(1));
    }

    @Test
    public void testApplyAssignMultiWlist() {
        Document doc = createDocument();
        assertNull(doc.getFieldValue("strwset"));
        WeightedSet<StringFieldValue> strwset = new WeightedSet<>(doc.getDataType().getField("strwset").getDataType());
        strwset.put(new StringFieldValue("hello hello"), 164);
        strwset.put(new StringFieldValue("blahdi blahdi"), 243);
        doc.setFieldValue("strwset", strwset);
        assertNotNull(doc.getFieldValue("strwset"));
        docUp.addFieldUpdate(assignMultiWset);
        docUp.applyTo(doc);
        assertEquals(2, ((WeightedSet)doc.getFieldValue("strwset")).size());
        WeightedSet docWset = (WeightedSet)doc.getFieldValue("strwset");
        assertEquals(Integer.valueOf(5), docWset.get(new StringFieldValue("assigned val 0")));
        assertEquals(Integer.valueOf(10), docWset.get(new StringFieldValue("assigned val 1")));
    }

    @Test
    public void testApplyAddMultiList() {
        Document doc = createDocument();
        assertNull(doc.getFieldValue("strarray"));

        docUp.addFieldUpdate(addMultiList);
        docUp.applyTo(doc);
        List<StringFieldValue> values = new ArrayList<>();
        values.add(new StringFieldValue("bo"));
        values.add(new StringFieldValue("ba"));
        values.add(new StringFieldValue("by"));
        assertEquals(values, doc.getFieldValue("strarray"));
    }

    @Test
    public void testApplyAddMultiWset() {
        Document doc = createDocument();
        assertNull(doc.getFieldValue("strwset"));

        WeightedSet<StringFieldValue> wset = new WeightedSet<>(doc.getDataType().getField("strwset").getDataType());
        wset.put(new StringFieldValue("this"), 5);
        wset.put(new StringFieldValue("is"), 10);
        wset.put(new StringFieldValue("a"), 15);
        wset.put(new StringFieldValue("test"), 20);
        doc.setFieldValue("strwset", wset);

        docUp.addFieldUpdate(addMultiWset);
        docUp.applyTo(doc);

        WeightedSet<StringFieldValue> values = new WeightedSet<>(doc.getDataType().getField("strwset").getDataType());
        values.put(new StringFieldValue("this"), 5);
        values.put(new StringFieldValue("is"), 143);
        values.put(new StringFieldValue("a"), 157);
        values.put(new StringFieldValue("test"), 20);
        values.put(new StringFieldValue("banana"), 137);
        values.put(new StringFieldValue("great"), 163);
        values.put(new StringFieldValue("fruit"), 189);
        assertEquals(values, doc.getFieldValue("strwset"));
    }

    @Test
    public void testUpdatesToTheSameFieldAreCombining() {
        DocumentType docType = new DocumentType("my_type");
        Field field = new Field("my_int", DataType.INT);
        docType.addField(field);

        DocumentUpdate update = new DocumentUpdate(docType, new DocumentId("id:ns:my_type::foo:"));
        update.addFieldUpdate(FieldUpdate.createAssign(field, new IntegerFieldValue(1)));
        update.addFieldUpdate(FieldUpdate.createAssign(field, new IntegerFieldValue(2)));

        assertEquals(1, update.fieldUpdates().size());
        FieldUpdate fieldUpdate = update.getFieldUpdate(field);
        assertNotNull(fieldUpdate);
        assertEquals(field, fieldUpdate.getField());
        assertEquals(2, fieldUpdate.getValueUpdates().size());
        ValueUpdate valueUpdate = fieldUpdate.getValueUpdate(0);
        assertNotNull(valueUpdate);
        assertTrue(valueUpdate instanceof AssignValueUpdate);
        assertEquals(new IntegerFieldValue(1), valueUpdate.getValue());
        assertNotNull(valueUpdate = fieldUpdate.getValueUpdate(1));
        assertTrue(valueUpdate instanceof AssignValueUpdate);
        assertEquals(new IntegerFieldValue(2), valueUpdate.getValue());
    }

    @Test
    public void testUpdateToWrongField() {
        DocumentType docType = new DocumentType("my_type");
        DocumentUpdate update = new DocumentUpdate(docType, new DocumentId("id:ns:my_type::foo:"));
        try {
            update.addFieldUpdate(FieldUpdate.createIncrement(new Field("my_int", DataType.INT), 1));
            fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
    }

    @Test
    public void testSerialize() {
        docUp.addFieldUpdate(assignSingle);
        docUp.addFieldUpdate(addMultiList);

        GrowableByteBuffer buf = new GrowableByteBuffer();
        docUp.serialize(DocumentSerializerFactory.create6(buf));
        buf.flip();

        try {
            FileOutputStream fos = new FileOutputStream("src/test/files/updateser.dat");
            fos.write(buf.array(), 0, buf.remaining());
            fos.close();
        } catch (Exception e) {
        }

        assertEquals((17 + 1) //docid id:ns:foobar:bar\0
                     + (6 + 1 + 2) //doctype foobar\0\0\0
                     + 4 //num field updates

                     //field update 1:
                     + (4 //field id
                        + 4 //num valueupdates

                        + (4 //valueUpdateClassID
                           + 1 //valuepresent
                           + (1 + 1 + 9 + 1)) //value

                        //field update 2
                        + (4 //field id
                           + 4 //num valueupdates

                           + (4  //valueUpdateClassID
                              + (4 + 4 + 4 + (1 + 1 + 2 + 1) + 4 + (1 + 1 + 2 + 1) + 4 + (1 + 1 + 2 + 1))))) //value
                        + 4 //num field path updates
                , buf.remaining());

        DocumentUpdate docUpDeser = new DocumentUpdate(DocumentDeserializerFactory.createHead(docMan, buf));
        assertEquals(docUp.getDocumentType(), docUpDeser.getDocumentType());
        assertEquals(docUp, docUpDeser);
    }

    @Ignore
    @Test
    public void testCppDocUpd() throws IOException {
        docMan = DocumentTestCase.setUpCppDocType();
        byte[] data = DocumentTestCase.readFile("src/tests/data/serializeupdatecpp.dat");
        DocumentDeserializer buf = DocumentDeserializerFactory.createHead(docMan, GrowableByteBuffer.wrap(data));

        DocumentType type = docMan.getDocumentType("serializetest");

        DocumentUpdate upd = new DocumentUpdate(buf);

        assertEquals(new DocumentId("id:ns:serializetest::update"), upd.getId());
        assertEquals(type, upd.getType());

        FieldUpdate serAssignFU = upd.getFieldUpdate(type.getField("intfield"));
        assertEquals(type.getField("intfield"), serAssignFU.getField());
        ValueUpdate serAssign = serAssignFU.getValueUpdate(0);
        assertEquals(ValueUpdate.ValueUpdateClassID.ASSIGN, serAssign.getValueUpdateClassID());
        assertEquals(new IntegerFieldValue(4), serAssign.getValue());

        ValueUpdate serArith = serAssignFU.getValueUpdate(1);
        assertEquals(ValueUpdate.ValueUpdateClassID.ARITHMETIC, serArith.getValueUpdateClassID());

        FieldUpdate serAddFU = upd.getFieldUpdate(type.getField("arrayoffloatfield"));
        assertEquals(type.getField("arrayoffloatfield"), serAddFU.getField());
        ValueUpdate serAdd1 = serAddFU.getValueUpdate(0);
        assertEquals(ValueUpdate.ValueUpdateClassID.ADD, serAdd1.getValueUpdateClassID());
        FloatFieldValue addParam1 = (FloatFieldValue)serAdd1.getValue();
        assertEquals(new FloatFieldValue(5.00f), addParam1);
        ValueUpdate serAdd2 = serAddFU.getValueUpdate(1);
        assertEquals(ValueUpdate.ValueUpdateClassID.ADD, serAdd2.getValueUpdateClassID());
        FloatFieldValue addparam2 = (FloatFieldValue)serAdd2.getValue();
        assertEquals(new FloatFieldValue(4.23f), addparam2);
        ValueUpdate serAdd3 = serAddFU.getValueUpdate(2);
        assertEquals(ValueUpdate.ValueUpdateClassID.ADD, serAdd3.getValueUpdateClassID());
        FloatFieldValue addparam3 = (FloatFieldValue)serAdd3.getValue();
        assertEquals(new FloatFieldValue(-1.00f), addparam3);

        FieldUpdate wsetFU = upd.getFieldUpdate(type.getField("wsfield"));
        assertEquals(type.getField("wsfield"), wsetFU.getField());
        assertEquals(2, wsetFU.size());
        ValueUpdate mapUpd = wsetFU.getValueUpdate(0);
        assertEquals(ValueUpdate.ValueUpdateClassID.MAP, mapUpd.getValueUpdateClassID());
        mapUpd = wsetFU.getValueUpdate(1);
        assertEquals(ValueUpdate.ValueUpdateClassID.MAP, mapUpd.getValueUpdateClassID());
    }

    @Test
    public void testGenerateSerializedFile() throws IOException {
        docMan = DocumentTestCase.setUpCppDocType();

        DocumentType type = docMan.getDocumentType("serializetest");
        DocumentUpdate upd = new DocumentUpdate(type, new DocumentId("id:ns:serializetest::update"));
        FieldUpdate serAssign = FieldUpdate.createAssign(type.getField("intfield"), new IntegerFieldValue(4));
        upd.addFieldUpdate(serAssign);
        FieldUpdate serClearField = FieldUpdate.createClearField(type.getField("floatfield"));
        upd.addFieldUpdate(serClearField);
        List<FloatFieldValue> arrayOfFloat = new ArrayList<>();
        arrayOfFloat.add(new FloatFieldValue(5.00f));
        arrayOfFloat.add(new FloatFieldValue(4.23f));
        arrayOfFloat.add(new FloatFieldValue(-1.00f));
        FieldUpdate serAdd = FieldUpdate.createAddAll(type.getField("arrayoffloatfield"), arrayOfFloat);
        upd.addFieldUpdate(serAdd);

        GrowableByteBuffer buf = new GrowableByteBuffer(100, 2.0f);
        upd.serialize(DocumentSerializerFactory.create6(buf));
        buf.flip();

        writeBufferToFile(buf, "src/tests/data/serializeupdatejava.dat");
    }

    private static void writeBufferToFile(GrowableByteBuffer buf, String fileName) throws IOException {
        FileOutputStream fos = new FileOutputStream(fileName);
        fos.write(buf.array(), 0, buf.remaining());
        fos.close();
    }

    @Test
    public void testRequireThatAddAllCombinesFieldUpdates() {
        DocumentType docType = new DocumentType("my_type");
        Field field = new Field("my_int", DataType.INT);
        docType.addField(field);

        FieldUpdate fooField = FieldUpdate.createAssign(field, new IntegerFieldValue(1));
        DocumentUpdate fooUpdate = new DocumentUpdate(docType, new DocumentId("id:ns:my_type::foo:"));
        fooUpdate.addFieldUpdate(fooField);

        FieldUpdate barField = FieldUpdate.createAssign(field, new IntegerFieldValue(2));
        DocumentUpdate barUpdate = new DocumentUpdate(docType, new DocumentId("id:ns:my_type::foo:"));
        barUpdate.addFieldUpdate(barField);

        fooUpdate.addAll(barUpdate);
        assertEquals(1, fooUpdate.fieldUpdates().size());
        FieldUpdate fieldUpdate = fooUpdate.getFieldUpdate(field);
        assertNotNull(fieldUpdate);
        assertEquals(field, fieldUpdate.getField());
        assertEquals(2, fieldUpdate.getValueUpdates().size());
        ValueUpdate valueUpdate = fieldUpdate.getValueUpdate(0);
        assertNotNull(valueUpdate);
        assertTrue(valueUpdate instanceof AssignValueUpdate);
        assertEquals(new IntegerFieldValue(1), valueUpdate.getValue());
        assertNotNull(valueUpdate = fieldUpdate.getValueUpdate(1));
        assertTrue(valueUpdate instanceof AssignValueUpdate);
        assertEquals(new IntegerFieldValue(2), valueUpdate.getValue());
    }

    @Test
    public void testGetAndRemoveByName() {
        DocumentType docType = new DocumentType("my_type");
        Field my_int = new Field("my_int", DataType.INT);
        Field your_int = new Field("your_int", DataType.INT);
        docType.addField(my_int);
        docType.addField(your_int);
        DocumentUpdate update = new DocumentUpdate(docType, new DocumentId("id:this:my_type::is:a:test"));

        update.addFieldUpdate(FieldUpdate.createAssign(my_int, new IntegerFieldValue(2)));
        assertNull(update.getFieldUpdate("none-existing-field"));
        assertNull(update.removeFieldUpdate("none-existing-field"));
        assertNull(update.getFieldUpdate("your_int"));
        assertEquals(new IntegerFieldValue(2), update.getFieldUpdate("my_int").getValueUpdate(0).getValue());
        assertNull(update.removeFieldUpdate("your_int"));
        assertEquals(new IntegerFieldValue(2), update.removeFieldUpdate("my_int").getValueUpdate(0).getValue());
        assertNull(update.getFieldUpdate("my_int"));

        update.addFieldUpdate(FieldUpdate.createAssign(my_int, new IntegerFieldValue(2)));
        assertNull(update.getFieldUpdate(your_int));
        assertEquals(new IntegerFieldValue(2), update.getFieldUpdate(my_int).getValueUpdate(0).getValue());
        assertNull(update.removeFieldUpdate(your_int));
        assertEquals(new IntegerFieldValue(2), update.removeFieldUpdate(my_int).getValueUpdate(0).getValue());
        assertNull(update.getFieldUpdate(my_int));
    }

    @Test
    public void testInstantiationAndEqualsHashCode() {
        DocumentType type = new DocumentType("doo");
        DocumentUpdate d1 = new DocumentUpdate(type, new DocumentId("id:this:doo::is:a:test"));
        DocumentUpdate d2 = new DocumentUpdate(type, "id:this:doo::is:a:test");

        assertEquals(d1, d2);
        assertEquals(d1, d1);
        assertEquals(d2, d1);
        assertEquals(d2, d2);
        assertFalse(d1.equals(new Object()));
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    public void testThatApplyingToWrongTypeFails() {
        DocumentType t1 = new DocumentType("doo");
        DocumentUpdate documentUpdate = new DocumentUpdate(t1, new DocumentId("id:this:doo::is:a:test"));

        DocumentType t2 = new DocumentType("foo");
        Document document = new Document(t2, "id:this:foo::is:another:test");

        try {
            documentUpdate.applyTo(document);
            fail("Should have gotten exception here.");
        } catch (IllegalArgumentException iae) {
            //ok!
        }
    }

    @Test
    public void testFieldUpdatesInDocUp() {
        DocumentType t1 = new DocumentType("doo");
        Field f1 = new Field("field1", DataType.STRING);
        Field f2 = new Field("field2", DataType.STRING);
        t1.addField(f1);
        t1.addField(f2);

        DocumentUpdate documentUpdate = new DocumentUpdate(t1, new DocumentId("id:ns:doo::is:a:test"));

        assertEquals(0, documentUpdate.size());

        FieldUpdate fu1 = FieldUpdate.createAssign(f1, new StringFieldValue("banana"));
        FieldUpdate fu2 = FieldUpdate.createAssign(f2, new StringFieldValue("apple"));
        documentUpdate.addFieldUpdate(fu1);

        assertEquals(1, documentUpdate.size());

        documentUpdate.clearFieldUpdates();

        assertEquals(0, documentUpdate.size());

        documentUpdate.addFieldUpdate(fu1);
        documentUpdate.addFieldUpdate(fu2);

        assertEquals(2, documentUpdate.size());


        assertSame(fu1, documentUpdate.getFieldUpdate(f1));

        try {
            documentUpdate.setFieldUpdates(null);
            fail("Should have gotten NullPointerException");
        } catch (NullPointerException npe) {
            //ok!
        }

        List<FieldUpdate> fus = new ArrayList<>();
        fus.add(fu1);
        fus.add(fu2);

        documentUpdate.setFieldUpdates(fus);
        assertEquals(2, documentUpdate.size());
        assertSame(fu1, documentUpdate.getFieldUpdate(fu1.getField()));
        assertSame(fu2, documentUpdate.getFieldUpdate(fu2.getField()));

        documentUpdate.removeFieldUpdate(fu2.getField());
        assertEquals(1, documentUpdate.size());
        assertSame(fu1, documentUpdate.getFieldUpdate(fu1.getField()));


        documentUpdate.toString();

        assertFalse(documentUpdate.isEmpty());

        Iterator<FieldPathUpdate> fpUpdates = documentUpdate.iterator();
        assertFalse(fpUpdates.hasNext());
    }

    @Test
    public void testAddAll() {
        DocumentType t1 = new DocumentType("doo");
        DocumentType t2 = new DocumentType("foo");
        Field f1 = new Field("field1", DataType.STRING);
        Field f2 = new Field("field2", DataType.STRING);
        t1.addField(f1);
        t2.addField(f2);

        DocumentUpdate du1 = new DocumentUpdate(t1, new DocumentId("id:this:doo::is:a:test"));
        DocumentUpdate du2 = new DocumentUpdate(t2, "id:this:foo::is:another:test");

        FieldUpdate fu1 = FieldUpdate.createAssign(f1, new StringFieldValue("banana"));
        FieldUpdate fu2 = FieldUpdate.createAssign(f2, new StringFieldValue("apple"));
        du1.addFieldUpdate(fu1);
        du2.addFieldUpdate(fu2);

        du1.addAll(null);

        try {
            du1.addAll(du2);
            fail("Should have gotten exception");
        } catch (IllegalArgumentException iae) {
            //ok!
        }

        DocumentUpdate du3 = new DocumentUpdate(t2, new DocumentId("id:this:foo::is:a:test"));

        try {
            du1.addAll(du3);
            fail("Should have gotten exception");
        } catch (IllegalArgumentException iae) {
            //ok!
        }
    }

    private void assertDocumentUpdateFlag(boolean createIfNonExistent, int value) {
        DocumentUpdateFlags f1 = new DocumentUpdateFlags();
        f1.setCreateIfNonExistent(createIfNonExistent);
        assertEquals(createIfNonExistent, f1.getCreateIfNonExistent());
        int combined = f1.injectInto(value);
        System.out.println("createIfNonExistent=" + createIfNonExistent + ", value=" + value + ", combined=" + combined);

        DocumentUpdateFlags f2 = DocumentUpdateFlags.extractFlags(combined);
        int extractedValue = DocumentUpdateFlags.extractValue(combined);
        assertEquals(createIfNonExistent, f2.getCreateIfNonExistent());
        assertEquals(value, extractedValue);
    }

    @Test
    public void testRequireThatDocumentUpdateFlagsIsWorking() {
        { // create-if-non-existent = true
            assertDocumentUpdateFlag(true, 0);
            assertDocumentUpdateFlag(true, 1);
            assertDocumentUpdateFlag(true, 2);
            assertDocumentUpdateFlag(true, 9999);
            assertDocumentUpdateFlag(true, 0xFFFFFFE);
            assertDocumentUpdateFlag(true, 0xFFFFFFF);
        }
        { // create-if-non-existent = false
            assertDocumentUpdateFlag(false, 0);
            assertDocumentUpdateFlag(false, 1);
            assertDocumentUpdateFlag(false, 2);
            assertDocumentUpdateFlag(false, 9999);
            assertDocumentUpdateFlag(false, 0xFFFFFFE);
            assertDocumentUpdateFlag(false, 0xFFFFFFF);
        }
    }

    @Test
    public void testRequireThatCreateIfNonExistentFlagIsSerializedAndDeserialized() {
        docUp.setCreateIfNonExistent(true);

        DocumentSerializer serializer = DocumentSerializerFactory.create6(new GrowableByteBuffer());
        docUp.serialize(serializer);
        serializer.getBuf().flip();

        DocumentDeserializer deserializer = DocumentDeserializerFactory.create6(docMan, serializer.getBuf());
        DocumentUpdate deserialized = new DocumentUpdate(deserializer);
        assertEquals(docUp, deserialized);
        assertTrue(deserialized.getCreateIfNonExistent());
    }

    @Test
    public void testThatAssignValueUpdateForTensorFieldCanBeApplied() {
        Document doc = createDocument();
        assertNull(doc.getFieldValue(tensorField));

        DocumentUpdate update = createTensorAssignUpdate();
        update.applyTo(doc);

        TensorFieldValue tensor = (TensorFieldValue) doc.getFieldValue(tensorField);
        assertEquals(createTensorFieldValue("{{x:0}:2.0}"), tensor);
    }

    @Test
    public void testThatAssignValueUpdateForTensorFieldCanBeSerializedAndDeserialized() {
        DocumentUpdate serializedUpdate = createTensorAssignUpdate();
        DocumentSerializer serializer = DocumentSerializerFactory.create6(new GrowableByteBuffer());
        serializedUpdate.serialize(serializer);
        serializer.getBuf().flip();

        DocumentDeserializer deserializer = DocumentDeserializerFactory.create6(docMan, serializer.getBuf());
        DocumentUpdate deserializedUpdate = new DocumentUpdate(deserializer);
        assertEquals(serializedUpdate, deserializedUpdate);
    }

    @Test
    public void testThatClearValueUpdateForTensorFieldCanBeApplied() {
        Document doc = createDocument();
        doc.setFieldValue(docType.getField(tensorField), createTensorFieldValue("{{x:0}:2.0}"));
        assertNotNull(doc.getFieldValue(tensorField));

        DocumentUpdate update = new DocumentUpdate(docType, new DocumentId(documentId));
        update.addFieldUpdate(FieldUpdate.createClear(docType.getField(tensorField)));
        update.applyTo(doc);

        assertNull(doc.getFieldValue(tensorField));
    }

    @Test
    public void testThatNonIdenticalAssignCanNotBePrunedAway() {
        Field field = docType.getField("strfoo");
        String expected = "some other value";
        Document doc = createDocument();
        doc.setFieldValue(field, "some value");
        DocumentUpdate update = new DocumentUpdate(docType, new DocumentId(documentId));
        update.addFieldUpdate(FieldUpdate.createAssign(field, new StringFieldValue(expected)));
        update.prune(doc);
        assertEquals(1, update.size());
        update.applyTo(doc);
        assertEquals(expected, doc.getFieldValue(field).getWrappedValue());
    }

    @Test
    public void testThatIdenticalAssignCanBePrunedAway() {
        Field field = docType.getField("strfoo");
        String expected = "some value";
        Document doc = createDocument();
        doc.setFieldValue(field, "some value");
        DocumentUpdate update = new DocumentUpdate(docType, new DocumentId(documentId));
        update.addFieldUpdate(FieldUpdate.createAssign(field,new StringFieldValue(expected)));
        update.prune(doc);
        assertEquals(0, update.size());
        update.applyTo(doc);
        assertEquals(expected, doc.getFieldValue(field).getWrappedValue());
    }

    @Test
    public void testThatIdenticalAssignCanBePrunedAwayIfLast() {
        Field field = docType.getField("strfoo");
        String expected = "some value";
        Document doc = createDocument();
        doc.setFieldValue(field, "some value");
        DocumentUpdate update = new DocumentUpdate(docType, new DocumentId(documentId));
        update.addFieldUpdate(FieldUpdate.createClearField(field));
        update.addFieldUpdate(FieldUpdate.createAssign(field, new StringFieldValue(expected)));
        update.prune(doc);
        assertEquals(0, update.size());
        update.applyTo(doc);
        assertEquals(expected, doc.getFieldValue(field).getWrappedValue());
    }

    @Test
    public void testThatIdenticalAssignCanNotBePrunedAwayIfNotLast() {
        Field field = docType.getField("strfoo");
        String expected = "some random value";
        Document doc = createDocument();
        doc.setFieldValue(field, "some value");
        DocumentUpdate update = new DocumentUpdate(docType, new DocumentId(documentId));
        update.addFieldUpdate(FieldUpdate.createAssign(field, new StringFieldValue("some value")));
        update.addFieldUpdate(FieldUpdate.createAssign(field, new StringFieldValue(expected)));
        update.prune(doc);
        assertEquals(1, update.size());
        update.applyTo(doc);
        assertEquals(expected, doc.getFieldValue(field).getWrappedValue());
    }

    @Test
    public void testThatClearCanBePrunedIfNoneExisting() {
        Field field = docType.getField("strfoo");
        Document doc = createDocument();
        StringFieldValue expected = new StringFieldValue("some value");
        expected.clear();
        doc.setFieldValue(field, expected);
        DocumentUpdate update = new DocumentUpdate(docType, new DocumentId(documentId));
        update.addFieldUpdate(FieldUpdate.createClearField(field));
        update.prune(doc);
        assertEquals(0, update.size());
        update.applyTo(doc);
        assertEquals(expected, doc.getFieldValue(field));
    }

    @Test
    public void testThatClearCanBePrunedIfEmpty() {
        Field field = docType.getField("strfoo");
        String expected = "";
        Document doc = createDocument();
        DocumentUpdate update = new DocumentUpdate(docType, new DocumentId(documentId));
        update.addFieldUpdate(FieldUpdate.createClearField(field));
        update.prune(doc);
        assertEquals(0, update.size());
        update.applyTo(doc);
        assertNull(doc.getFieldValue(field));
    }

    @Test
    public void testThatClearCanBePrunedIfNoneExistingAndLast() {
        Field field = docType.getField("strfoo");
        String expected = "";
        Document doc = createDocument();
        DocumentUpdate update = new DocumentUpdate(docType, new DocumentId(documentId));
        update.addFieldUpdate(FieldUpdate.createAssign(field, new StringFieldValue("some value")));
        update.addFieldUpdate(FieldUpdate.createClearField(field));
        update.prune(doc);
        assertEquals(0, update.size());
        update.applyTo(doc);
        assertNull(doc.getFieldValue(field));
    }

    private static TensorFieldValue createTensorFieldValue(String tensor) {
        return new TensorFieldValue(Tensor.from(tensor));
    }

    private DocumentUpdate createTensorAssignUpdate() {
        DocumentUpdate result = new DocumentUpdate(docType, new DocumentId(documentId));
        result.addFieldUpdate(FieldUpdate.createAssign(docType.getField(tensorField),
                              createTensorFieldValue("{{x:0}:2.0}")));
        return result;
    }

    private static class TensorUpdateSerializeFixture {
        private DocumentTypeManager docMan;
        private DocumentType docType;

        public TensorUpdateSerializeFixture() {
            docMan = new DocumentTypeManager();
            docType = new DocumentType("test");
            docType.addField("sparse_tensor", new TensorDataType(TensorType.fromSpec("tensor(x{})")));
            docType.addField("dense_tensor", new TensorDataType(TensorType.fromSpec("tensor(x[4])")));
            docMan.registerDocumentType(docType);
        }

        Field getField(String name) {
            return docType.getField(name);
        }

        TensorFieldValue createTensor() {
            return new TensorFieldValue(Tensor.from("tensor(x{})", "{{x:2}:5, {x:3}:7}"));
        }

        DocumentUpdate createUpdate() {
            var result = new DocumentUpdate(docType, "id:test:test::0");

            result.addFieldUpdate(FieldUpdate.create(getField("sparse_tensor"))
                    .addValueUpdate(new AssignValueUpdate(createTensor()))
                    .addValueUpdate(new TensorAddUpdate(createTensor()))
                    .addValueUpdate(new TensorRemoveUpdate(createTensor())));

            result.addFieldUpdate(FieldUpdate.create(getField("dense_tensor"))
                    .addValueUpdate(new TensorModifyUpdate(TensorModifyUpdate.Operation.REPLACE, createTensor()))
                    .addValueUpdate(new TensorModifyUpdate(TensorModifyUpdate.Operation.ADD, createTensor()))
                    .addValueUpdate(new TensorModifyUpdate(TensorModifyUpdate.Operation.MULTIPLY, createTensor())));
            return result;
        }

        void serializeUpdateToFile(DocumentUpdate update, String fileName) throws IOException {
            GrowableByteBuffer buf = new GrowableByteBuffer(100, 2.0f);
            update.serialize(DocumentSerializerFactory.createHead(buf));
            buf.flip();

            writeBufferToFile(buf, fileName);
        }

        DocumentUpdate deserializeUpdateFromFile(String fileName) throws IOException {
            byte[] data = DocumentTestCase.readFile(fileName);
            DocumentDeserializer buf = DocumentDeserializerFactory.createHead(docMan, GrowableByteBuffer.wrap(data));
            return new DocumentUpdate(buf);
        }
    }

    @Test
    public void tensor_update_file_cpp_can_be_deserialized() throws IOException {
        var f = new TensorUpdateSerializeFixture();
        var update = f.deserializeUpdateFromFile("src/tests/data/serialize-tensor-update-cpp.dat");
        assertEquals(f.createUpdate(), update);
    }

    @Test
    public void generate_serialized_tensor_update_file_java() throws IOException {
        var f = new TensorUpdateSerializeFixture();
        var update = f.createUpdate();
        f.serializeUpdateToFile(update, "src/tests/data/serialize-tensor-update-java.dat");
    }

}
