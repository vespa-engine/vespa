// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;

public class TensorModifyUpdateTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void convert_to_compatible_type_with_only_mapped_dimensions() {
        assertConvertToCompatible("tensor(x{})", "tensor(x[])");
        assertConvertToCompatible("tensor(x{})", "tensor(x[10])");
        assertConvertToCompatible("tensor(x{})", "tensor(x{})");
        assertConvertToCompatible("tensor(x{},y{},z{})", "tensor(x[],y[10],z{})");
    }

    private static void assertConvertToCompatible(String expectedType, String inputType) {
        assertEquals(expectedType, TensorModifyUpdate.convertToCompatibleType(TensorType.fromSpec(inputType)).toString());
    }

    @Test
    public void use_of_incompatible_tensor_type_throws() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Tensor type 'tensor(x[3])' is not compatible as it contains some indexed dimensions");
        new TensorModifyUpdate(TensorModifyUpdate.Operation.REPLACE,
                new TensorFieldValue(Tensor.from("tensor(x[3])", "{{x:1}:3}")));
    }
}
