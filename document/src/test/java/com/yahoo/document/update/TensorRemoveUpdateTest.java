// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.Tensor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TensorRemoveUpdateTest {

    @Test
    public void apply_remove_update_operations() {
        assertApplyTo("{{x:0,y:0}:2, {x:0,y:1}:3}", "{{x:0,y:1}:1}", "{{x:0,y:0}:2}");
        assertApplyTo("{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:0}:1,{x:0,y:1}:1}", "{}");
        assertApplyTo("{}", "{{x:0,y:0}:1}", "{}");
        assertApplyTo("{{x:0,y:0}:2, {x:0,y:1}:3}", "{}", "{{x:0,y:0}:2, {x:0,y:1}:3}");
    }

    private void assertApplyTo(String init, String update, String expected) {
        String spec = "tensor(x{},y{})";
        TensorFieldValue initialFieldValue = new TensorFieldValue(Tensor.from(spec, init));
        TensorRemoveUpdate removeUpdate = new TensorRemoveUpdate(new TensorFieldValue(Tensor.from(spec, update)));
        TensorFieldValue updatedFieldValue = (TensorFieldValue) removeUpdate.applyTo(initialFieldValue);
        assertEquals(Tensor.from(spec, expected), updatedFieldValue.getTensor().get());
    }

}
