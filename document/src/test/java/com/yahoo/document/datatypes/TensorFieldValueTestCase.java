// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


/**
 * @author geirst
 */
public class TensorFieldValueTestCase {

    private static TensorFieldValue createFieldValue(String tensorString) {
        return new TensorFieldValue(Tensor.from(tensorString));
    }

    @Test
    public void requireThatDifferentFieldValueTypesAreNotEqual() {
        assertFalse(createFieldValue("{{x:0}:2.0}").equals(new IntegerFieldValue(5)));
    }

    @Test
    public void requireThatDifferentTensorTypesWithEmptyValuesAreNotEqual() {
        TensorFieldValue field1 = new TensorFieldValue(new TensorType.Builder().mapped("x").build());
        TensorFieldValue field2 = new TensorFieldValue(new TensorType.Builder().indexed("y").build());
        assertFalse(field1.equals(field2));
    }

    @Test
    public void requireThatDifferentTensorValuesAreNotEqual() {
        TensorFieldValue field1 = createFieldValue("{{x:0}:2.0}");
        TensorFieldValue field2 = createFieldValue("{{x:0}:3.0}");
        assertFalse(field1.equals(field2));
        assertFalse(field1.equals(new TensorFieldValue(TensorType.empty)));
    }

    @Test
    public void requireThatSameTensorValueIsEqual() {
        Tensor tensor = Tensor.from("{{x:0}:2.0}");
        TensorFieldValue field1 = new TensorFieldValue(tensor);
        TensorFieldValue field2 = new TensorFieldValue(tensor);
        assertTrue(field1.equals(field1));
        assertTrue(field1.equals(field2));
        assertTrue(field1.equals(createFieldValue("{{x:0}:2.0}")));
    }

    @Test
    public void requireThatToStringWorks() {
        TensorFieldValue field1 = createFieldValue("{{x:0}:2.0}");
        assertEquals("tensor(x{}):{0:2.0}", field1.toString());
        TensorFieldValue field2 = new TensorFieldValue(TensorType.fromSpec("tensor(x{})"));
        assertEquals("null", field2.toString());
    }

    @Test
    public void requireThatSerializationHappens() {
        TensorFieldValue orig = createFieldValue("{{x:0}:2.0}");
        assertEquals("tensor(x{}):{0:2.0}", orig.toString());
        assertTrue(orig.getWrappedValue() != null);
        Optional<byte[]> serializedBytes = orig.getSerializedTensor();
        assertTrue(serializedBytes.isPresent());
        Optional<byte[]> otherSerialized = orig.getSerializedTensor();
        assertTrue(otherSerialized.isPresent());
        assertTrue(serializedBytes.get() == otherSerialized.get());

        TensorFieldValue copy = new TensorFieldValue();
        assertTrue(copy.getSerializedTensor().isEmpty());
        copy.assignSerializedTensor(serializedBytes.get());
        assertFalse(copy.getSerializedTensor().isEmpty());
        otherSerialized = copy.getSerializedTensor();
        assertTrue(serializedBytes.get() == otherSerialized.get());

        copy = new TensorFieldValue();
        assertTrue(copy.getTensorType().isEmpty());
        copy.assignSerializedTensor(serializedBytes.get());
        assertFalse(copy.getTensorType().isEmpty());
        assertEquals(copy.getTensorType().get(), orig.getTensorType().get());

        copy = new TensorFieldValue();
        assertTrue(copy.getTensor().isEmpty());
        copy.assignSerializedTensor(serializedBytes.get());
        assertFalse(copy.getTensor().isEmpty());

        copy = new TensorFieldValue();
        assertTrue(copy.getTensor().isEmpty());
        copy.assignSerializedTensor(serializedBytes.get());
        assertFalse(copy.getTensor().isEmpty());
        assertEquals(copy.getTensor().get(), orig.getTensor().get());

        copy = new TensorFieldValue();
        assertTrue(copy.getWrappedValue() == null);
        copy.assignSerializedTensor(serializedBytes.get());
        assertFalse(copy.getWrappedValue() == null);
        assertEquals(copy.getWrappedValue(), orig.getWrappedValue());
    }

    @Test
    public void requireThatAssignConvertsFloatTensorToBfloat16Tensor() {
        TensorFieldValue target = new TensorFieldValue(TensorType.fromSpec("tensor<bfloat16>(x[2])"));
        target.assign(new TensorFieldValue(Tensor.from("tensor<float>(x[2]):[1.25,2.5]")));

        assertEquals(Optional.of(TensorType.fromSpec("tensor<bfloat16>(x[2])")), target.getTensorType());
        assertEquals(Optional.of(Tensor.from("tensor<bfloat16>(x[2]):[1.25,2.5]")), target.getTensor());
    }

    @Test
    public void requireThatAssignConvertsSerializedFloatTensorToBfloat16Tensor() {
        Tensor sourceTensor = Tensor.from("tensor<float>(x[2]):[1.25,2.5]");
        TensorFieldValue serializedSource = new TensorFieldValue(sourceTensor.type());
        serializedSource.assignSerializedTensor(new TensorFieldValue(sourceTensor).getSerializedTensor().get());

        TensorFieldValue target = new TensorFieldValue(TensorType.fromSpec("tensor<bfloat16>(x[2])"));
        target.assign(serializedSource);

        assertEquals(Optional.of(TensorType.fromSpec("tensor<bfloat16>(x[2])")), target.getTensorType());
        assertEquals(Optional.of(Tensor.from("tensor<bfloat16>(x[2]):[1.25,2.5]")), target.getTensor());
    }

}
