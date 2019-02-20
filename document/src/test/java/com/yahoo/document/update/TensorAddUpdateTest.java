// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.Tensor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;

public class TensorAddUpdateTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void apply_add_update_operations() {
        assertApplyTo("tensor(x{},y{})", "{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:2}:3}", "{{x:0,y:0}:1,{x:0,y:1}:2,{x:0,y:2}:3}");
        assertApplyTo("tensor(x{},y{})", "{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:1}:3}", "{{x:0,y:0}:1,{x:0,y:1}:3}");
        assertApplyTo("tensor(x{},y{})", "{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:1}:3,{x:0,y:2}:4}", "{{x:0,y:0}:1,{x:0,y:1}:3,{x:0,y:2}:4}");
        assertApplyTo("tensor(x{},y{})", "{}", "{{x:0,y:0}:5}", "{{x:0,y:0}:5}");
        assertApplyTo("tensor(x{},y{})", "{{x:0,y:0}:1, {x:0,y:1}:2}", "{}", "{{x:0,y:0}:1, {x:0,y:1}:2}");

        assertApplyTo("tensor(x{},y[3])", "{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:2}:3}", "{{x:0,y:0}:1,{x:0,y:1}:2,{x:0,y:2}:3}");
        assertApplyTo("tensor(x{},y[3])", "{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:1}:3}", "{{x:0,y:0}:1,{x:0,y:1}:3,{x:0,y:2}:0}");
        assertApplyTo("tensor(x{},y[3])", "{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:1}:3,{x:0,y:2}:4}", "{{x:0,y:0}:1,{x:0,y:1}:3,{x:0,y:2}:4}");
        assertApplyTo("tensor(x{},y[3])", "{}", "{{x:0,y:0}:5}", "{{x:0,y:0}:5,{x:0,y:1}:0,{x:0,y:2}:0}");
        assertApplyTo("tensor(x{},y[3])", "{{x:0,y:0}:1, {x:0,y:1}:2}", "{}", "{{x:0,y:0}:1,{x:0,y:1}:2,{x:0,y:2}:0}");
    }

    private Tensor updateField(String spec, String init, String update) {
        TensorFieldValue initialFieldValue = new TensorFieldValue(Tensor.from(spec, init));
        TensorAddUpdate addUpdate = new TensorAddUpdate(new TensorFieldValue(Tensor.from("tensor(x{},y{})", update)));
        return ((TensorFieldValue) addUpdate.applyTo(initialFieldValue)).getTensor().get();
    }

    private void assertApplyTo(String spec, String init, String update, String expected) {
        assertEquals(Tensor.from(spec, expected), updateField(spec, init, update));
    }

}
