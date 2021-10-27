// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.Tensor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TensorAddUpdateTest {

    @Test
    public void apply_add_update_operations() {
        assertApplyTo("{{x:0,y:0}:1, {x:0,y:1}:2}", "{{x:0,y:2}:3}", "{{x:0,y:0}:1,{x:0,y:1}:2,{x:0,y:2}:3}");
    }

    private void assertApplyTo(String init, String update, String expected) {
        String spec = "tensor(x{},y{})";
        TensorFieldValue initialFieldValue = new TensorFieldValue(Tensor.from(spec, init));
        TensorAddUpdate addUpdate = new TensorAddUpdate(new TensorFieldValue(Tensor.from(spec, update)));
        Tensor updated = ((TensorFieldValue) addUpdate.applyTo(initialFieldValue)).getTensor().get();
        assertEquals(Tensor.from(spec, expected), updated);
    }

}
