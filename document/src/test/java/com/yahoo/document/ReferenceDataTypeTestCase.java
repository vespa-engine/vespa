// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.ReferenceFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class ReferenceDataTypeTestCase {

    private static DocumentType createDocumentType(String name) {
        DocumentType type = new DocumentType(name);
        type.addField(new Field("foo", DataType.STRING));
        return type;
    }

    @Test
    public void parameters_are_propagated_to_base_data_type() {
        DocumentType docType = createDocumentType("bjarne");
        ReferenceDataType refType = new ReferenceDataType(docType, 1234);
        assertEquals("Reference<bjarne>", refType.getName());
        assertEquals(1234, refType.getId());
        assertEquals(docType, refType.getTargetType());
    }

    @Test
    public void empty_reference_field_value_instance_can_be_created_from_type() {
        ReferenceDataType refType = new ReferenceDataType(createDocumentType("foo"), 123);
        ReferenceFieldValue fv = refType.createFieldValue();
        assertNotNull(fv);
        assertEquals(refType, fv.getDataType());
    }

    @Test
    public void reference_data_type_has_reference_field_value_class() {
        ReferenceDataType refType = new ReferenceDataType(createDocumentType("foo"), 123);
        assertEquals(ReferenceFieldValue.class, refType.getValueClass());
    }

    private static class MultiTypeFixture {
        final DocumentType docType                        = createDocumentType("bar");
        final ReferenceDataType  refType                  = new ReferenceDataType(docType, 123);
        final ReferenceDataType  refTypeClone             = new ReferenceDataType(docType, 123);
        final ReferenceDataType  typeWithDifferentId      = new ReferenceDataType(docType, 456);
        // Technically this shouldn't be possible, right? But test it anyway.
        final ReferenceDataType  typeWithDifferentDocType = new ReferenceDataType(createDocumentType("stuff"), 123);
    }

    @Test
    public void equals_checks_document_type_and_type_id() {
        final MultiTypeFixture fixture = new MultiTypeFixture();

        // Note: the default DataType.equals method actually satisfies this, since we already
        // give it a type-parameterized name and id
        assertFalse(fixture.refType.equals(null));
        assertFalse(fixture.refType.equals(DataType.STRING));
        assertFalse(fixture.refType.equals(fixture.typeWithDifferentId));
        assertFalse(fixture.refType.equals(fixture.typeWithDifferentDocType));
        assertTrue(fixture.refType.equals(fixture.refType));
        assertTrue(fixture.refType.equals(fixture.refTypeClone));
    }

    @Test
    public void type_value_compatibility_checks_target_type() {
        final MultiTypeFixture fixture = new MultiTypeFixture();

        assertFalse(fixture.refType.isValueCompatible(null));
        assertFalse(fixture.refType.isValueCompatible(new StringFieldValue("baz")));
        assertFalse(fixture.refType.isValueCompatible(fixture.typeWithDifferentId.createFieldValue()));
        assertFalse(fixture.refType.isValueCompatible(fixture.typeWithDifferentDocType.createFieldValue()));
        assertTrue(fixture.refType.isValueCompatible(fixture.refType.createFieldValue()));
        assertTrue(fixture.refType.isValueCompatible(fixture.refTypeClone.createFieldValue()));
    }

}
