// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.Tensor;
import org.junit.Test;

import java.io.IOException;

import static com.yahoo.document.serialization.SerializationTestUtils.deserializeDocument;
import static com.yahoo.document.serialization.SerializationTestUtils.serializeDocument;
import static org.junit.Assert.assertEquals;

/**
 * @author geirst
 */
public class TensorFieldValueSerializationTestCase {

    private final static String TENSOR_FIELD = "my_tensor";
    private final static String TENSOR_FILES = "src/test/resources/tensor/";
    private final static TestDocumentFactory docFactory =
            new TestDocumentFactory(createDocType(), "id:test:my_type::foo");

    private static DocumentType createDocType() {
        DocumentType type = new DocumentType("my_type");
        type.addField(TENSOR_FIELD, DataType.TENSOR);
        return type;
    }

    @Test
    public void requireThatTensorFieldValueIsSerializedAndDeserialized() {
        assertSerialization(new TensorFieldValue());
        assertSerialization(createTensor("{}"));
        assertSerialization(createTensor("{{dimX:a,dimY:bb}:2.0,{dimX:ccc,dimY:dddd}:3.0,{dimX:e,dimY:ff}:5.0}"));
    }

    @Test
    public void requireThatSerializationMatchesCpp() throws IOException {
        assertSerializationMatchesCpp("non_existing_tensor", new TensorFieldValue());
        assertSerializationMatchesCpp("empty_tensor", createTensor("{}"));
        assertSerializationMatchesCpp("multi_cell_tensor",
                createTensor("{{dimX:a,dimY:bb}:2.0,{dimX:ccc,dimY:dddd}:3.0,{dimX:e,dimY:ff}:5.0}"));
    }

    private static void assertSerialization(TensorFieldValue tensor) {
        Document document = docFactory.createDocument();
        document.setFieldValue(TENSOR_FIELD, tensor);
        byte[] buf = serializeDocument(document);
        Document deserializedDocument = deserializeDocument(buf, docFactory);
        assertEquals(document, deserializedDocument);
        assertEquals(tensor, deserializedDocument.getFieldValue(TENSOR_FIELD));
    }

    private static void assertSerializationMatchesCpp(String fileName, TensorFieldValue tensor) throws IOException {
        Document document = docFactory.createDocument();
        document.setFieldValue(TENSOR_FIELD, tensor);
        SerializationTestUtils.assertSerializationMatchesCpp(TENSOR_FILES, fileName, document, docFactory);
    }

    private static TensorFieldValue createTensor(String tensor) {
        return new TensorFieldValue(Tensor.from(tensor));
    }

}
