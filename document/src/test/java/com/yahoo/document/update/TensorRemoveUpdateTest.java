// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.Tensor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TensorRemoveUpdateTest {

    @Test
    public void apply_remove_update_operations_sparse() {
        assertSparseApplyTo("{{x:0,y:0}:2, {x:0,y:1}:3}", "{{x:0,y:1}:1}", "{{x:0,y:0}:2}");
        assertSparseApplyTo("{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:0}:1,{x:0,y:1}:1}", "{}");
        assertSparseApplyTo("{}", "{{x:0,y:0}:1}", "{}");
        assertSparseApplyTo("{{x:0,y:0}:2, {x:0,y:1}:3}", "{}", "{{x:0,y:0}:2, {x:0,y:1}:3}");
    }

    @Test
    public void apply_remove_update_operations_mixed() {
        assertMixedApplyTo("{{x:0,y:0}:2, {x:0,y:1}:3}", "{{x:0}:1}", "{}");
        assertMixedApplyTo("{{x:0,y:0}:1, {x:1,y:0}:2}", "{{x:0}:1}", "{{x:1,y:0}:2,{x:1,y:1}:0,{x:1,y:2}:0}");
        assertMixedApplyTo("{}", "{{x:0}:1}", "{}");
        assertMixedApplyTo("{{x:0,y:0}:2, {x:0,y:1}:3}", "{}", "{{x:0,y:0}:2, {x:0,y:1}:3}");
    }

    private void assertSparseApplyTo(String init, String update, String expected) {
        assertApplyTo("tensor(x{},y{})", "tensor(x{},y{})", init, update, expected);
    }

    private void assertMixedApplyTo(String init, String update, String expected) {
        assertApplyTo("tensor(x{},y[3])", "tensor(x{})", init, update, expected);
    }

    private void assertApplyTo(String spec, String updateSpec, String init, String update, String expected) {
        TensorFieldValue initialFieldValue = new TensorFieldValue(Tensor.from(spec, init));
        TensorRemoveUpdate removeUpdate = new TensorRemoveUpdate(new TensorFieldValue(Tensor.from(updateSpec, update)));
        TensorFieldValue updatedFieldValue = (TensorFieldValue) removeUpdate.applyTo(initialFieldValue);
        assertEquals(Tensor.from(spec, expected), updatedFieldValue.getTensor().get());
    }

}
