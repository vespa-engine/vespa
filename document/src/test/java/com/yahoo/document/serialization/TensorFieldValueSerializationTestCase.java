// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import java.io.IOException;

import static com.yahoo.document.serialization.SerializationTestUtils.deserializeDocument;
import static com.yahoo.document.serialization.SerializationTestUtils.serializeDocument;
import static org.junit.Assert.assertEquals;

/**
 * @author geirst
 */
public class TensorFieldValueSerializationTestCase {

    private final static TensorType tensorType = new TensorType.Builder().mapped("dimX").mapped("dimY").build();
    private final static String TENSOR_FIELD = "my_tensor";
    private final static String TENSOR_FILES = "src/test/resources/tensor/";
    private final static TestDocumentFactory docFactory = new TestDocumentFactory(createDocType(),
                                                                                  "id:test:my_type::foo");

    private static DocumentType createDocType() {
        DocumentType type = new DocumentType("my_type");
        type.addField(TENSOR_FIELD, new TensorDataType(tensorType));
        return type;
    }

    @Test
    public void requireThatTensorFieldValueIsSerializedAndDeserialized() {
        assertSerialization(new TensorFieldValue(tensorType));
        assertSerialization(createTensor(tensorType, "{}"));
        assertSerialization(createTensor(tensorType, "{{dimX:a,dimY:bb}:2.0,{dimX:ccc,dimY:dddd}:3.0,{dimX:e,dimY:ff}:5.0}"));
    }

    @Test
    public void requireThatSerializationMatchesCpp() throws IOException {
        assertSerializationMatchesCpp("non_existing_tensor", new TensorFieldValue(tensorType));
        assertSerializationMatchesCpp("empty_tensor", createTensor(tensorType, "{}"));
        assertSerializationMatchesCpp("multi_cell_tensor",
                createTensor(tensorType, "{{dimX:a,dimY:bb}:2.0,{dimX:ccc,dimY:dddd}:3.0,{dimX:e,dimY:ff}:5.0}"));
    }

    private static void assertSerialization(TensorFieldValue tensor) {
        SerializationTestUtils.assertFieldInDocumentSerialization(docFactory, TENSOR_FIELD, tensor);
    }

    private static void assertSerializationMatchesCpp(String fileName, TensorFieldValue tensor) throws IOException {
        Document document = docFactory.createDocument();
        document.setFieldValue(TENSOR_FIELD, tensor);
        SerializationTestUtils.assertSerializationMatchesCpp(TENSOR_FILES, fileName, document, docFactory);
    }

    private static TensorFieldValue createTensor(TensorType type, String tensorCellString) {
        return new TensorFieldValue(Tensor.from(type, tensorCellString));
    }

}
