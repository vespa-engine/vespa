// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.tensor.Tensor;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


/**
 * @author geirst
 */
public class TensorFieldValueTestCase {

    private static TensorFieldValue createFieldValue(String tensor) {
        return new TensorFieldValue(Tensor.from(tensor));
    }

    @Test
    public void requireThatDifferentFieldValueTypesAreNotEqual() {
        assertFalse(createFieldValue("{{x:0}:2.0}").equals(new IntegerFieldValue(5)));
    }

    @Test
    public void requireThatDifferentTensorValuesAreNotEqual() {
        TensorFieldValue lhs = createFieldValue("{{x:0}:2.0}");
        TensorFieldValue rhs = createFieldValue("{{x:0}:3.0}");
        assertFalse(lhs.equals(rhs));
        assertFalse(lhs.equals(new TensorFieldValue()));
    }

    @Test
    public void requireThatSameTensorValueIsEqual() {
        Tensor tensor = Tensor.from("{{x:0}:2.0}");
        TensorFieldValue lhs = new TensorFieldValue(tensor);
        TensorFieldValue rhs = new TensorFieldValue(tensor);
        assertTrue(lhs.equals(lhs));
        assertTrue(lhs.equals(rhs));
        assertTrue(lhs.equals(createFieldValue("{{x:0}:2.0}")));
    }
}
