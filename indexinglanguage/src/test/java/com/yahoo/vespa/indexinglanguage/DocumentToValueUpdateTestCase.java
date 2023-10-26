// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.*;
import com.yahoo.document.update.*;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class DocumentToValueUpdateTestCase {

    @Test
    public void requireThatSddocnameFieldIsIgnored() {
        DocumentType docType = new DocumentType("my_type");
        docType.addField(new Field("sddocname", DataType.STRING));

        ValueUpdate valueUpd = ValueUpdate.createAssign(new StringFieldValue("69"));
        Document doc = FieldUpdateHelper.newPartialDocument(docType, null, docType.getField("sddocname"), valueUpd);
        doc.setFieldValue("sddocname", new StringFieldValue("96"));

        UpdateAdapter adapter = FieldUpdateAdapter.fromPartialUpdate(new SimpleDocumentAdapter(null, doc), valueUpd);
        assertNull(adapter.getOutput());
    }

    @Test
    public void requireThatEmptyFieldUpdatesAreIgnored() {
        DocumentType docType = new DocumentType("my_type");
        docType.addField(new Field("my_int", DataType.INT));
        docType.addField(new Field("my_str", DataType.STRING));

        ValueUpdate valueUpd = ValueUpdate.createAssign(new IntegerFieldValue(69));
        Document doc = FieldUpdateHelper.newPartialDocument(docType, null, docType.getField("my_int"), valueUpd);
        doc.setFieldValue("my_int", new IntegerFieldValue(96));

        UpdateAdapter adapter = FieldUpdateAdapter.fromPartialUpdate(new SimpleDocumentAdapter(null, doc), valueUpd);
        DocumentUpdate docUpd = adapter.getOutput();
        assertNotNull(docUpd);
        assertEquals(1, docUpd.fieldUpdates().size());
        assertEquals("my_int", docUpd.fieldUpdates().iterator().next().getField().getName());
    }

    @Test
    public void requireThatIntegerAssignIsConverted() {
        DocumentType docType = new DocumentType("my_type");
        docType.addField(new Field("my_int", DataType.INT));

        ValueUpdate valueUpd = ValueUpdate.createAssign(new IntegerFieldValue(69));
        Document doc = FieldUpdateHelper.newPartialDocument(docType, null, docType.getField("my_int"), valueUpd);
        doc.setFieldValue("my_int", new IntegerFieldValue(96));

        UpdateAdapter adapter = FieldUpdateAdapter.fromPartialUpdate(new SimpleDocumentAdapter(null, doc), valueUpd);
        DocumentUpdate docUpd = adapter.getOutput();
        assertNotNull(docUpd);
        assertEquals(1, docUpd.fieldUpdates().size());

        FieldUpdate fieldUpd = docUpd.fieldUpdates().iterator().next();
        assertNotNull(fieldUpd);

        assertEquals(docType.getField("my_int"), fieldUpd.getField());
        assertEquals(1, fieldUpd.getValueUpdates().size());
        assertNotNull(valueUpd = fieldUpd.getValueUpdate(0));
        assertTrue(valueUpd instanceof AssignValueUpdate);
        assertEquals(new IntegerFieldValue(96), valueUpd.getValue());
    }

    @Test
    public void requireThatClearFieldIsConverted() {
        DocumentType docType = new DocumentType("my_type");
        docType.addField(new Field("my_int", DataType.INT));

        ValueUpdate valueUpd = ValueUpdate.createClear();
        Document doc = FieldUpdateHelper.newPartialDocument(docType, null, docType.getField("my_int"), valueUpd);
        assertNotNull(doc.getFieldValue("my_int"));

        UpdateAdapter adapter = FieldUpdateAdapter.fromPartialUpdate(new SimpleDocumentAdapter(null, doc), valueUpd);
        DocumentUpdate docUpd = adapter.getOutput();
        assertNotNull(docUpd);
        assertEquals(1, docUpd.fieldUpdates().size());

        FieldUpdate fieldUpd = docUpd.fieldUpdates().iterator().next();
        assertNotNull(fieldUpd);

        assertEquals(docType.getField("my_int"), fieldUpd.getField());
        assertEquals(1, fieldUpd.getValueUpdates().size());
        assertNotNull(valueUpd = fieldUpd.getValueUpdate(0));
        assertTrue(valueUpd instanceof ClearValueUpdate);
    }

    @Test
    public void requireThatStringAssignIsConverted() {
        DocumentType docType = new DocumentType("my_type");
        docType.addField(new Field("my_str", DataType.STRING));

        ValueUpdate valueUpd = ValueUpdate.createAssign(new StringFieldValue("69"));
        Document doc = FieldUpdateHelper.newPartialDocument(docType, null, docType.getField("my_str"), valueUpd);
        doc.setFieldValue("my_str", new StringFieldValue("96"));

        UpdateAdapter adapter = FieldUpdateAdapter.fromPartialUpdate(new SimpleDocumentAdapter(null, doc), valueUpd);
        DocumentUpdate docUpd = adapter.getOutput();
        assertNotNull(docUpd);
        assertEquals(1, docUpd.fieldUpdates().size());

        FieldUpdate fieldUpd = docUpd.fieldUpdates().iterator().next();
        assertNotNull(fieldUpd);

        assertEquals(docType.getField("my_str"), fieldUpd.getField());
        assertEquals(1, fieldUpd.getValueUpdates().size());
        assertNotNull(valueUpd = fieldUpd.getValueUpdate(0));
        assertTrue(valueUpd instanceof AssignValueUpdate);
        assertEquals(new StringFieldValue("96"), valueUpd.getValue());
    }

    @Test
    public void requireThatStructAssignIsConverted() {
        DocumentType docType = new DocumentType("my_type");
        StructDataType structType = new StructDataType("my_struct");
        structType.addField(new Field("b", DataType.INT));
        docType.addField(new Field("a", structType));

        Struct struct = structType.createFieldValue();
        struct.setFieldValue("b", new IntegerFieldValue(69));
        ValueUpdate valueUpd = ValueUpdate.createAssign(struct);
        Document doc = FieldUpdateHelper.newPartialDocument(docType, null, docType.getField("a"), valueUpd);
        assertNotNull(doc);

        FieldValue obj = doc.getFieldValue("a");
        assertTrue(obj instanceof Struct);
        struct = (Struct)obj;
        struct.setFieldValue("b", new IntegerFieldValue(96));

        UpdateAdapter adapter = FieldUpdateAdapter.fromPartialUpdate(new SimpleDocumentAdapter(null, doc), valueUpd);
        DocumentUpdate docUpd = adapter.getOutput();
        assertNotNull(docUpd);
        assertEquals(1, docUpd.fieldUpdates().size());

        FieldUpdate fieldUpd = docUpd.fieldUpdates().iterator().next();
        assertNotNull(fieldUpd);

        assertEquals(docType.getField("a"), fieldUpd.getField());
        assertEquals(1, fieldUpd.getValueUpdates().size());
        assertNotNull(valueUpd = fieldUpd.getValueUpdate(0));
        assertTrue(valueUpd instanceof AssignValueUpdate);
        assertEquals(struct, valueUpd.getValue());
    }

    @Test
    public void requireThatArrayElementAddIsConverted() {
        DocumentType docType = new DocumentType("my_type");
        ArrayDataType arrType = DataType.getArray(DataType.STRING);
        docType.addField(new Field("my_arr", arrType));

        ValueUpdate valueUpd = ValueUpdate.createAdd(new StringFieldValue("foo"));
        Document doc = FieldUpdateHelper.newPartialDocument(docType, null, docType.getField("my_arr"), valueUpd);
        assertNotNull(doc);

        FieldValue obj = doc.getFieldValue("my_arr");
        assertTrue(obj instanceof Array);
        Array<StringFieldValue> arr = (Array<StringFieldValue>)obj;
        arr.set(0, new StringFieldValue("bar"));

        UpdateAdapter adapter = FieldUpdateAdapter.fromPartialUpdate(new SimpleDocumentAdapter(null, doc), valueUpd);
        DocumentUpdate docUpd = adapter.getOutput();
        assertNotNull(docUpd);
        assertEquals(1, docUpd.fieldUpdates().size());

        FieldUpdate fieldUpd = docUpd.fieldUpdates().iterator().next();
        assertNotNull(fieldUpd);

        assertEquals(docType.getField("my_arr"), fieldUpd.getField());
        assertEquals(1, fieldUpd.getValueUpdates().size());
        assertNotNull(valueUpd = fieldUpd.getValueUpdate(0));
        assertTrue(valueUpd instanceof AddValueUpdate);
        assertEquals(new StringFieldValue("bar"), valueUpd.getValue());
    }

    @Test
    public void requireThatArrayElementRemoveIsConverted() {
        DocumentType docType = new DocumentType("my_type");
        ArrayDataType arrType = DataType.getArray(DataType.STRING);
        docType.addField(new Field("my_arr", arrType));

        ValueUpdate valueUpd = ValueUpdate.createRemove(new StringFieldValue("foo"));
        Document doc = FieldUpdateHelper.newPartialDocument(docType, null, docType.getField("my_arr"), valueUpd);
        assertNotNull(doc);

        FieldValue obj = doc.getFieldValue("my_arr");
        assertTrue(obj instanceof Array);
        Array<StringFieldValue> arr = (Array<StringFieldValue>)obj;
        arr.set(0, new StringFieldValue("bar"));

        UpdateAdapter adapter = FieldUpdateAdapter.fromPartialUpdate(new SimpleDocumentAdapter(null, doc), valueUpd);
        DocumentUpdate docUpd = adapter.getOutput();
        assertNotNull(docUpd);
        assertEquals(1, docUpd.fieldUpdates().size());

        FieldUpdate fieldUpd = docUpd.fieldUpdates().iterator().next();
        assertNotNull(fieldUpd);

        assertEquals(docType.getField("my_arr"), fieldUpd.getField());
        assertEquals(1, fieldUpd.getValueUpdates().size());
        assertNotNull(valueUpd = fieldUpd.getValueUpdate(0));
        assertTrue(valueUpd instanceof RemoveValueUpdate);
        assertEquals(new StringFieldValue("bar"), valueUpd.getValue());
    }

    @Test
    public void requireThatArrayOfStructElementAddIsConverted() {
        DocumentType docType = new DocumentType("my_type");
        StructDataType structType = new StructDataType("my_struct");
        structType.addField(new Field("b", DataType.INT));
        docType.addField(new Field("a", DataType.getArray(structType)));

        Struct struct = structType.createFieldValue();
        struct.setFieldValue("b", new IntegerFieldValue(69));
        ValueUpdate valueUpd = ValueUpdate.createAdd(struct);
        Document doc = FieldUpdateHelper.newPartialDocument(docType, null, docType.getField("a"), valueUpd);
        assertNotNull(doc);

        FieldValue obj = doc.getFieldValue("a");
        assertTrue(obj instanceof Array);
        Array<Struct> arr = (Array<Struct>)obj;
        struct = structType.createFieldValue();
        struct.setFieldValue("b", new IntegerFieldValue(96));
        arr.set(0, struct);

        UpdateAdapter adapter = FieldUpdateAdapter.fromPartialUpdate(new SimpleDocumentAdapter(null, doc), valueUpd);
        DocumentUpdate docUpd = adapter.getOutput();
        assertNotNull(docUpd);
        assertEquals(1, docUpd.fieldUpdates().size());

        FieldUpdate fieldUpd = docUpd.fieldUpdates().iterator().next();
        assertNotNull(fieldUpd);

        assertEquals(docType.getField("a"), fieldUpd.getField());
        assertEquals(1, fieldUpd.getValueUpdates().size());
        assertNotNull(valueUpd = fieldUpd.getValueUpdate(0));
        assertTrue(valueUpd instanceof AddValueUpdate);
        assertEquals(struct, valueUpd.getValue());
    }

    @Test
    public void requireThatWsetElementAssignIsConverted() {
        DocumentType docType = new DocumentType("my_type");
        WeightedSetDataType wsetType = DataType.getWeightedSet(DataType.STRING);
        docType.addField(new Field("my_wset", wsetType));

        ValueUpdate valueUpd = ValueUpdate.createMap(new StringFieldValue("foo"), ValueUpdate.createAssign(new IntegerFieldValue(69)));
        Document doc = FieldUpdateHelper.newPartialDocument(docType, null, docType.getField("my_wset"), valueUpd);
        assertNotNull(doc);

        FieldValue obj = doc.getFieldValue("my_wset");
        assertTrue(obj instanceof WeightedSet);
        WeightedSet<StringFieldValue> wset = (WeightedSet<StringFieldValue>)obj;
        wset.put(new StringFieldValue("foo"), 96);

        UpdateAdapter adapter = FieldUpdateAdapter.fromPartialUpdate(new SimpleDocumentAdapter(null, doc), valueUpd);
        DocumentUpdate docUpd = adapter.getOutput();
        assertNotNull(docUpd);
        assertEquals(1, docUpd.fieldUpdates().size());

        FieldUpdate fieldUpd = docUpd.fieldUpdates().iterator().next();
        assertNotNull(fieldUpd);

        assertEquals(docType.getField("my_wset"), fieldUpd.getField());
        assertEquals(1, fieldUpd.getValueUpdates().size());
        assertNotNull(valueUpd = fieldUpd.getValueUpdate(0));
        assertTrue(valueUpd instanceof MapValueUpdate);
        assertEquals(new StringFieldValue("foo"), valueUpd.getValue());
        assertNotNull(valueUpd = ((MapValueUpdate)valueUpd).getUpdate());
        assertTrue(valueUpd instanceof AssignValueUpdate);
        assertEquals(new IntegerFieldValue(96), valueUpd.getValue());
    }

    @Test
    public void requireThatWsetElementAddIsConverted() {
        DocumentType docType = new DocumentType("my_type");
        WeightedSetDataType wsetType = DataType.getWeightedSet(DataType.STRING);
        docType.addField(new Field("my_wset", wsetType));

        ValueUpdate valueUpd = ValueUpdate.createAdd(new StringFieldValue("foo"), 69);
        Document doc = FieldUpdateHelper.newPartialDocument(docType, null, docType.getField("my_wset"), valueUpd);
        assertNotNull(doc);

        FieldValue obj = doc.getFieldValue("my_wset");
        assertTrue(obj instanceof WeightedSet);
        WeightedSet<StringFieldValue> wset = (WeightedSet<StringFieldValue>)obj;
        wset.put(new StringFieldValue("foo"), 96);

        UpdateAdapter adapter = FieldUpdateAdapter.fromPartialUpdate(new SimpleDocumentAdapter(null, doc), valueUpd);
        DocumentUpdate docUpd = adapter.getOutput();
        assertNotNull(docUpd);
        assertEquals(1, docUpd.fieldUpdates().size());

        FieldUpdate fieldUpd = docUpd.fieldUpdates().iterator().next();
        assertNotNull(fieldUpd);

        assertEquals(docType.getField("my_wset"), fieldUpd.getField());
        assertEquals(1, fieldUpd.getValueUpdates().size());
        assertNotNull(valueUpd = fieldUpd.getValueUpdate(0));
        assertTrue(valueUpd instanceof AddValueUpdate);
        assertEquals(new StringFieldValue("foo"), valueUpd.getValue());
        assertEquals(96, ((AddValueUpdate)valueUpd).getWeight());
    }

    @Test
    public void requireThatWsetElementRemoveIsConverted() {
        DocumentType docType = new DocumentType("my_type");
        WeightedSetDataType wsetType = DataType.getWeightedSet(DataType.STRING);
        docType.addField(new Field("my_wset", wsetType));

        ValueUpdate valueUpd = ValueUpdate.createRemove(new StringFieldValue("foo"));
        Document doc = FieldUpdateHelper.newPartialDocument(docType, null, docType.getField("my_wset"), valueUpd);
        assertNotNull(doc);

        FieldValue obj = doc.getFieldValue("my_wset");
        assertTrue(obj instanceof WeightedSet);
        WeightedSet<StringFieldValue> wset = (WeightedSet<StringFieldValue>)obj;
        wset.remove(new StringFieldValue("foo"));
        wset.add(new StringFieldValue("bar"));

        UpdateAdapter adapter = FieldUpdateAdapter.fromPartialUpdate(new SimpleDocumentAdapter(null, doc), valueUpd);
        DocumentUpdate docUpd = adapter.getOutput();
        assertNotNull(docUpd);
        assertEquals(1, docUpd.fieldUpdates().size());

        FieldUpdate fieldUpd = docUpd.fieldUpdates().iterator().next();
        assertNotNull(fieldUpd);

        assertEquals(docType.getField("my_wset"), fieldUpd.getField());
        assertEquals(1, fieldUpd.getValueUpdates().size());
        assertNotNull(valueUpd = fieldUpd.getValueUpdate(0));
        assertTrue(valueUpd instanceof RemoveValueUpdate);
        assertEquals(new StringFieldValue("bar"), valueUpd.getValue());
    }

    private static class TensorFixture {

        private DocumentType docType = new DocumentType("test");
        private String tensorField;

        public TensorFixture() {
            this("sparse_tensor", "tensor(x{})");
        }

        public TensorFixture(String tensorField, String typeSpec) {
            this.tensorField = tensorField;
            docType.addField(new Field(tensorField, DataType.getTensor(TensorType.fromSpec(typeSpec))));
        }

        Document assertTensorUpdateNotApplied(ValueUpdate tensorUpdate) {
            Document result = FieldUpdateHelper.newPartialDocument(docType, null, docType.getField(tensorField), tensorUpdate);
            assertNotNull(result);

            TensorFieldValue tensor = (TensorFieldValue) result.getFieldValue(tensorField);
            assertTrue(tensor.getTensor().isEmpty());

            return result;
        }

        public <T extends ValueUpdate> void assertTensorUpdatePassesThrough(T tensorUpdate, Document doc) {
            UpdateAdapter adapter = FieldUpdateAdapter.fromPartialUpdate(new SimpleDocumentAdapter(null, doc), tensorUpdate);

            DocumentUpdate docUpdate = adapter.getOutput();
            assertNotNull(docUpdate);
            assertEquals(1, docUpdate.fieldUpdates().size());

            FieldUpdate fieldUpdate = docUpdate.fieldUpdates().iterator().next();
            assertNotNull(fieldUpdate);
            assertEquals(1, fieldUpdate.getValueUpdates().size());
            ValueUpdate valueUpdate = fieldUpdate.getValueUpdate(0);
            T actValueUpdate = (T) valueUpdate;
            assertEquals(tensorUpdate, actValueUpdate);
        }
    }

    @Test
    public void tensor_modify_update_passes_through_unmodified() {
        TensorFixture f = new TensorFixture();
        TensorModifyUpdate modifyUpdate = new TensorModifyUpdate(TensorModifyUpdate.Operation.REPLACE,
                new TensorFieldValue(Tensor.from("tensor(x{})", "{{x:a}:3}")));

        Document doc = f.assertTensorUpdateNotApplied(modifyUpdate);
        f.assertTensorUpdatePassesThrough(modifyUpdate, doc);
    }

    @Test
    public void tensor_add_update_passes_through_unmodified() {
        TensorFixture f = new TensorFixture();
        TensorAddUpdate addUpdate = new TensorAddUpdate(new TensorFieldValue(Tensor.from("tensor(x{})", "{{x:a}:3}")));

        Document doc = f.assertTensorUpdateNotApplied(addUpdate);
        f.assertTensorUpdatePassesThrough(addUpdate, doc);
    }

    @Test
    public void tensor_remove_update_passes_through_unmodified() {
        TensorFixture f = new TensorFixture();
        TensorRemoveUpdate removeUpdate = new TensorRemoveUpdate(new TensorFieldValue(Tensor.from("tensor(x{})", "{{x:a}:1}")));
        Document doc = f.assertTensorUpdateNotApplied(removeUpdate);
        f.assertTensorUpdatePassesThrough(removeUpdate, doc);
    }

}
