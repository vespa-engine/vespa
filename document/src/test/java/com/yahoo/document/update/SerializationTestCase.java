// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.serialization.*;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Before;
import org.junit.Test;

import java.io.FileOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * NOTE: Roundtrip serialization (JSON -> DocumentUpdate -> ByteBuffer -> DocumentUpdate -> JSON) of updates is tested in DocumentUpdateJsonSerializerTest.
 *       Consider adding new test cases to that test class instead.
 *
 * @author bratseth
 */
public class SerializationTestCase {

    private DocumentType documentType;

    private Field field;
    private final static TensorType sparseTensorType = new TensorType.Builder().mapped("x").mapped("y").build();
    private final static TensorType denseTensorType = new TensorType.Builder().indexed("x", 2).indexed("y", 3).build();
    private Field sparseTensorField;
    private Field denseTensorField;

    @Before
    public void setUp() {
        documentType = new DocumentType("document1");
        field = new Field("field1", DataType.getArray(DataType.STRING));
        documentType.addField(field);
        sparseTensorField = new Field("sparse_tensor", new TensorDataType(sparseTensorType));
        denseTensorField = new Field("dense_tensor", new TensorDataType(denseTensorType));
        documentType.addField(sparseTensorField);
        documentType.addField(denseTensorField);
    }

    @Test
    public void testAddSerialization() {
        FieldUpdate update = FieldUpdate.createAdd(field, new StringFieldValue("value1"));
        DocumentSerializer buffer = DocumentSerializerFactory.create6();
        update.serialize(buffer);

        buffer.getBuf().rewind();

        try{
            FileOutputStream fos = new FileOutputStream("src/test/files/addfieldser.dat");
            fos.write(buffer.getBuf().array(), 0, buffer.getBuf().remaining());
            fos.close();
        } catch (Exception e) {}

        FieldUpdate deserializedUpdate = new FieldUpdate(DocumentDeserializerFactory.create6(new DocumentTypeManager(), buffer.getBuf()), documentType, Document.SERIALIZED_VERSION);
        assertEquals("'field1' [add value1 1]", deserializedUpdate.toString());
    }

    @Test
    public void testClearSerialization() {
        FieldUpdate update = FieldUpdate.createClear(field);
        DocumentSerializer buffer = DocumentSerializerFactory.create6();
        update.serialize(buffer);

        buffer.getBuf().rewind();
        FieldUpdate deserializedUpdate = new FieldUpdate(DocumentDeserializerFactory.create6(new DocumentTypeManager(), buffer.getBuf()), documentType, Document.SERIALIZED_VERSION);

        assertEquals("'field1' [clear]", deserializedUpdate.toString());
    }

    @Test
    public void test_tensor_modify_update_serialization_with_dense_tensor() {
        String tensorString = "{{x:1,y:2}:2}";
        FieldUpdate update = createTensorModifyUpdate(denseTensorField, denseTensorType, tensorString);

        FieldUpdate deserializedUpdate = roundtripSerialize(update);
        TensorModifyUpdate modifyUpdate = expectTensorModifyUpdate(deserializedUpdate, "dense_tensor");

        assertEquals(TensorModifyUpdate.Operation.REPLACE, modifyUpdate.getOperation());
        assertEquals(TensorType.fromSpec("tensor(x{},y{})"), modifyUpdate.getValue().getDataType().getTensorType());
        assertEquals(createTensor(sparseTensorType, tensorString), modifyUpdate.getValue());
        assertEquals(update, deserializedUpdate);
    }

    @Test
    public void test_tensor_modify_update_serialization_with_sparse_tensor() {
        String tensorString = "{{x:a,y:b}:2}";
        FieldUpdate update = createTensorModifyUpdate(sparseTensorField, sparseTensorType, tensorString);

        FieldUpdate deserializedUpdate = roundtripSerialize(update);
        TensorModifyUpdate modifyUpdate = expectTensorModifyUpdate(deserializedUpdate, "sparse_tensor");

        assertEquals(TensorModifyUpdate.Operation.REPLACE, modifyUpdate.getOperation());
        assertEquals(TensorType.fromSpec("tensor(x{},y{})"), modifyUpdate.getValue().getDataType().getTensorType());
        assertEquals(createTensor(sparseTensorType, tensorString), modifyUpdate.getValue());
        assertEquals(update, deserializedUpdate);
    }

    private static FieldUpdate createTensorModifyUpdate(Field tensorField, TensorType tensorType, String tensorString) {
        FieldUpdate result = new FieldUpdate(tensorField);
        // Note that the tensor type is converted to only have mapped dimensions.
        TensorFieldValue tensor = createTensor(TensorModifyUpdate.convertToCompatibleType(tensorType), tensorString);
        result.addValueUpdate(new TensorModifyUpdate(TensorModifyUpdate.Operation.REPLACE, tensor));
        return result;
    }

    private static TensorFieldValue createTensor(TensorType type, String tensorCellString) {
        return new TensorFieldValue(Tensor.from(type, tensorCellString));
    }

    private static GrowableByteBuffer serializeUpdate(FieldUpdate update) {
        DocumentSerializer buffer = DocumentSerializerFactory.createHead(new GrowableByteBuffer());
        update.serialize(buffer);
        buffer.getBuf().rewind();
        return buffer.getBuf();
    }

    private FieldUpdate deserializeUpdate(GrowableByteBuffer buffer) {
        return new FieldUpdate(DocumentDeserializerFactory.createHead(new DocumentTypeManager(), buffer), documentType, Document.SERIALIZED_VERSION);
    }

    private FieldUpdate roundtripSerialize(FieldUpdate update) {
        GrowableByteBuffer buffer = serializeUpdate(update);
        return deserializeUpdate(buffer);
    }

    private static TensorModifyUpdate expectTensorModifyUpdate(FieldUpdate update, String tensorFieldName) {
        assertEquals(tensorFieldName, update.getField().getName());
        assertEquals(1, update.getValueUpdates().size());
        ValueUpdate valueUpdate = update.getValueUpdate(0);
        if (!(valueUpdate instanceof TensorModifyUpdate)) {
            throw new IllegalStateException("Expected tensorModifyUpdate");
        }
        return (TensorModifyUpdate)valueUpdate;
    }
}
