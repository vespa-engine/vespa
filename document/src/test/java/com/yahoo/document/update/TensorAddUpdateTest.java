// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.Tensor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TensorAddUpdateTest {

    @Test
    public void apply_add_update_operations() {
        assertApplyTo("{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:2}:3}", "{{x:0,y:0}:1,{x:0,y:1}:2,{x:0,y:2}:3}");
        assertApplyTo("{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:1}:3}", "{{x:0,y:0}:1,{x:0,y:1}:3}");
        assertApplyTo("{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:1}:3,{x:0,y:2}:4}", "{{x:0,y:0}:1,{x:0,y:1}:3,{x:0,y:2}:4}");
        assertApplyTo("{}", "{{x:0,y:0}:5}", "{{x:0,y:0}:5}");
        assertApplyTo("{{x:0,y:0}:1, {x:0,y:1}:2}", "{}", "{{x:0,y:0}:1, {x:0,y:1}:2}");
    }

    private void assertApplyTo(String init, String update, String expected) {
        String spec = "tensor(x{},y{})";
        TensorFieldValue initialFieldValue = new TensorFieldValue(Tensor.from(spec, init));
        TensorAddUpdate addUpdate = new TensorAddUpdate(new TensorFieldValue(Tensor.from(spec, update)));
        TensorFieldValue updatedFieldValue = (TensorFieldValue) addUpdate.applyTo(initialFieldValue);
        assertEquals(Tensor.from(spec, expected), updatedFieldValue.getTensor().get());
    }

}
