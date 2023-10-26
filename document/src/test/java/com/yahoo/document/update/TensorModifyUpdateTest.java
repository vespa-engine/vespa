// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.update.TensorModifyUpdate.Operation;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import java.util.Optional;

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

    @Test
    public void apply_modify_update_operations_with_default_cell_value() {
        assertApplyTo("tensor(x{})", "tensor(x{})", Operation.MULTIPLY, true,
            "{{x:a}:1,{x:b}:2}", "{{x:b}:3}", "{{x:a}:1,{x:b}:6}");

        assertApplyTo("tensor(x{})", "tensor(x{})", Operation.MULTIPLY, true,
                "{{x:a}:1,{x:b}:2}", "{{x:b}:3,{x:c}:4}", "{{x:a}:1,{x:b}:6,{x:c}:4}");

        assertApplyTo("tensor(x{},y[3])", "tensor(x{},y{})", Operation.ADD, true,
                "{{x:a,y:0}:3,{x:a,y:1}:4,{x:a,y:2}:5}",
                "{{x:a,y:0}:6,{x:b,y:1}:7,{x:b,y:2}:8,{x:c,y:0}:9}",
                "{{x:a,y:0}:9,{x:a,y:1}:4,{x:a,y:2}:5," +
                        "{x:b,y:0}:0,{x:b,y:1}:7,{x:b,y:2}:8," +
                        "{x:c,y:0}:9,{x:c,y:1}:0,{x:c,y:2}:0}");

        // NOTE: The default cell value (1.0) used for MULTIPLY operation doesn't have any effect for tensors
        // with only indexed dimensions, as the dense subspace is always represented (with default cell value 0.0).
        assertApplyTo("tensor(x[3])", "tensor(x{})", Operation.MULTIPLY, true,
                "{{x:0}:2}", "{{x:1}:3}", "{{x:0}:2,{x:1}:0,{x:2}:0}");
    }

    private void assertApplyTo(String spec, Operation op, String input, String update, String expected) {
        assertApplyTo(spec, "tensor(x{},y{})", op, false, input, update, expected);
    }

    private void assertApplyTo(String inputSpec, String updateSpec, Operation op, boolean createNonExistingCells, String input, String update, String expected) {
        TensorFieldValue inputFieldValue = new TensorFieldValue(Tensor.from(inputSpec, input));
        TensorModifyUpdate modifyUpdate = new TensorModifyUpdate(op, new TensorFieldValue(Tensor.from(updateSpec, update)), createNonExistingCells);
        TensorFieldValue updatedFieldValue = (TensorFieldValue) modifyUpdate.applyTo(inputFieldValue);
        assertEquals(Tensor.from(inputSpec, expected), updatedFieldValue.getTensor().get());
    }
}
