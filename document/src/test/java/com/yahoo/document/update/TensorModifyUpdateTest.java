// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.update.TensorModifyUpdate.Operation;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TensorModifyUpdateTest {

    @Test
    public void convert_to_compatible_type_with_only_mapped_dimensions() {
        assertConvertToCompatible("tensor(x{})", "tensor(x[])");
        assertConvertToCompatible("tensor(x{})", "tensor(x[10])");
        assertConvertToCompatible("tensor(x{})", "tensor(x{})");
        assertConvertToCompatible("tensor(x{},y{},z{})", "tensor(x[],y[10],z{})");
        assertConvertToCompatible("tensor(x{},y{})", "tensor(x{},y[3])");
    }

    private static void assertConvertToCompatible(String expectedType, String inputType) {
        assertEquals(expectedType, TensorModifyUpdate.convertDimensionsToMapped(TensorType.fromSpec(inputType)).toString());
    }

    @Test
    public void use_of_incompatible_tensor_type_throws() {
        try {
            new TensorModifyUpdate(TensorModifyUpdate.Operation.REPLACE,
                    new TensorFieldValue(Tensor.from("tensor(x[3])", "{{x:1}:3}")));
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Tensor type 'tensor(x[3])' is not compatible as it has no mapped dimensions",
                    e.getMessage());
        }
    }

    @Test
    public void apply_modify_update_operations() {
        assertApplyTo("tensor(x{},y{})", Operation.REPLACE,
                "{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:1}:0}", "{{x:0,y:0}:1,{x:0,y:1}:0}");
        assertApplyTo("tensor(x[1],y[2])", Operation.ADD,
                "{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:1}:3}", "{{x:0,y:0}:1,{x:0,y:1}:5}");
        assertApplyTo("tensor(x{},y[2])", Operation.MULTIPLY,
                "{{x:0,y:0}:3, {x:0,y:1}:2}", "{{x:0,y:1}:3}", "{{x:0,y:0}:3,{x:0,y:1}:6}");
    }

    private void assertApplyTo(String spec, Operation op, String init, String update, String expected) {
        TensorFieldValue initialFieldValue = new TensorFieldValue(Tensor.from(spec, init));
        TensorModifyUpdate modifyUpdate = new TensorModifyUpdate(op, new TensorFieldValue(Tensor.from("tensor(x{},y{})", update)));
        TensorFieldValue updatedFieldValue = (TensorFieldValue) modifyUpdate.applyTo(initialFieldValue);
        assertEquals(Tensor.from(spec, expected), updatedFieldValue.getTensor().get());
    }

}
