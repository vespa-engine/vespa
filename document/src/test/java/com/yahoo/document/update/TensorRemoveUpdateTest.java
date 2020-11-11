// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.yolean.Exceptions;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TensorRemoveUpdateTest {

    @Test
    public void apply_remove_update_operations() {
        assertApplyTo("{{x:0,y:0}:2, {x:0,y:1}:3}", "{{x:0,y:1}:1}", "{{x:0,y:0}:2}");
    }

    private void assertApplyTo(String init, String update, String expected) {
        String spec = "tensor(x{},y{})";
        TensorFieldValue initialFieldValue = new TensorFieldValue(Tensor.from(spec, init));
        TensorRemoveUpdate removeUpdate = new TensorRemoveUpdate(new TensorFieldValue(Tensor.from(spec, update)));
        TensorFieldValue updatedFieldValue = (TensorFieldValue) removeUpdate.applyTo(initialFieldValue);
        assertEquals(Tensor.from(spec, expected), updatedFieldValue.getTensor().get());
    }

    @Test
    public void verify_compatible_type_throws_on_mismatch() {
        // Contains an indexed dimension, which is not allowed.
        illegalTensorRemoveUpdate("tensor(x{},y[1])", "{{x:a,y:0}:1}", "tensor(x{},y[1])",
                "Unexpected type 'tensor(x{},y[1])' in remove update. Expected dimensions to be a subset of 'tensor(x{})'");

        // Sparse dimension is not found in the original type.
        illegalTensorRemoveUpdate("tensor(y{})", "{{y:a}:1}", "tensor(x{},z[2])",
                "Unexpected type 'tensor(y{})' in remove update. Expected dimensions to be a subset of 'tensor(x{})'");
    }

    private void illegalTensorRemoveUpdate(String updateType, String updateTensor, String originalType, String expectedMessage) {
        try {
            var value = new TensorFieldValue(Tensor.from(updateType, updateTensor));
            new TensorRemoveUpdate(value).verifyCompatibleType(TensorType.fromSpec(originalType));
            fail("Expected exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals(expectedMessage, Exceptions.toMessageString(expected));
        }
    }

}
