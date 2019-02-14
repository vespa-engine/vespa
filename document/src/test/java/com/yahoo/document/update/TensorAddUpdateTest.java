// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.Field;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
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
        DocumentTypeManager types = new DocumentTypeManager();
        DocumentType x = new DocumentType("x");
        x.addField(new Field("f", new TensorDataType(TensorType.fromSpec(spec))));
        types.registerDocumentType(x);

        Document document = new Document(types.getDocumentType("x"), new DocumentId("doc:test:x"));
        document.setFieldValue("f", new TensorFieldValue(Tensor.from(spec, init)));

        FieldUpdate.create(document.getField("f"))
                .addValueUpdate(new TensorAddUpdate(new TensorFieldValue(Tensor.from(spec, update))))
                .applyTo(document);
        Tensor result = ((TensorFieldValue) document.getFieldValue("f")).getTensor().get();
        assertEquals(Tensor.from(spec, expected), result);
    }

}
