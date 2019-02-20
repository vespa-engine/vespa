// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.Tensor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TensorAddUpdateTest {

    @Test
    public void apply_add_update_operations_sparse() {
        assertSparseApplyTo("{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:2}:3}", "{{x:0,y:0}:1,{x:0,y:1}:2,{x:0,y:2}:3}");
        assertSparseApplyTo("{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:1}:3}", "{{x:0,y:0}:1,{x:0,y:1}:3}");
        assertSparseApplyTo("{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:1}:3,{x:0,y:2}:4}", "{{x:0,y:0}:1,{x:0,y:1}:3,{x:0,y:2}:4}");
        assertSparseApplyTo("{}", "{{x:0,y:0}:5}", "{{x:0,y:0}:5}");
        assertSparseApplyTo("{{x:0,y:0}:1, {x:0,y:1}:2}", "{}", "{{x:0,y:0}:1, {x:0,y:1}:2}");
    }

    @Test
    public void apply_add_update_operations_mixed() {
        assertMixedApplyTo("{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:2}:3}", "{{x:0,y:0}:0,{x:0,y:1}:0,{x:0,y:2}:3}");
        assertMixedApplyTo("{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:1}:3}", "{{x:0,y:0}:0,{x:0,y:1}:3,{x:0,y:2}:0}");
        assertMixedApplyTo("{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:1}:3,{x:0,y:2}:4}", "{{x:0,y:0}:0,{x:0,y:1}:3,{x:0,y:2}:4}");
        assertMixedApplyTo("{}", "{{x:0,y:0}:5}", "{{x:0,y:0}:5,{x:0,y:1}:0,{x:0,y:2}:0}");
        assertMixedApplyTo("{{x:0,y:0}:1, {x:0,y:1}:2}", "{}", "{{x:0,y:0}:1,{x:0,y:1}:2,{x:0,y:2}:0}");
    }

    private void assertSparseApplyTo(String init, String update, String expected) {
        assertApplyTo("tensor(x{},y{})", init, update, expected);
    }

    private void assertMixedApplyTo(String init, String update, String expected) {
        assertApplyTo("tensor(x{},y[3])", init, update, expected);
    }

    private void assertApplyTo(String spec, String init, String update, String expected) {
        TensorFieldValue initialFieldValue = new TensorFieldValue(Tensor.from(spec, init));
        TensorAddUpdate addUpdate = new TensorAddUpdate(new TensorFieldValue(Tensor.from(spec, update)));
        Tensor updated = ((TensorFieldValue) addUpdate.applyTo(initialFieldValue)).getTensor().get();
        assertEquals(Tensor.from(spec, expected), updated);
    }

}
