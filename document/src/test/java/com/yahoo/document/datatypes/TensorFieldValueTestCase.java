// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

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
}
