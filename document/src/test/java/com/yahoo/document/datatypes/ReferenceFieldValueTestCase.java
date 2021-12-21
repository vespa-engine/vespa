// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.ReferenceDataType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author vekterli
 * @since 6.65
 */
public class ReferenceFieldValueTestCase {

    private static DocumentType createDocumentType(String name) {
        DocumentType type = new DocumentType(name);
        type.addField(new Field("foo", DataType.STRING));
        return type;
    }

    private static ReferenceDataType createReferenceType(String documentTypeName, int id) {
        return new ReferenceDataType(createDocumentType(documentTypeName), id);
    }

    private static ReferenceDataType referenceTypeFoo() {
        return createReferenceType("foo", 1234);
    }

    private static ReferenceDataType referenceTypeFooDifferentId() {
        return createReferenceType("foo", 5678);
    }

    private static ReferenceDataType referenceTypeBar() {
        return createReferenceType("bar", 7654);
    }

    private static DocumentId docId(String idString) {
        return new DocumentId(idString);
    }

    @Test
    public void default_constructed_reference_is_empty_and_bound_to_type() {
        ReferenceFieldValue value = new ReferenceFieldValue(referenceTypeFoo());
        assertFalse(value.getDocumentId().isPresent());
        assertEquals(referenceTypeFoo(), value.getDataType());
    }

    @Test
    public void factory_method_creates_empty_reference_bound_to_type() {
        ReferenceFieldValue value = ReferenceFieldValue.createEmptyWithType(referenceTypeFoo());
        assertFalse(value.getDocumentId().isPresent());
        assertEquals(referenceTypeFoo(), value.getDataType());
    }

    @Test
    public void reference_can_be_constructed_with_id() {
        DocumentId id = docId("id:ns:foo::itsa-me");
        ReferenceFieldValue value = new ReferenceFieldValue(referenceTypeFoo(), id);
        assertTrue(value.getDocumentId().isPresent());
        assertEquals(id, value.getDocumentId().get());
    }

    @Test
    public void can_explicitly_set_new_id_for_existing_reference() {
        ReferenceFieldValue value = new ReferenceFieldValue(referenceTypeFoo());
        DocumentId newId = docId("id:ns:foo::wario-time");
        value.setDocumentId(newId);
        assertTrue(value.getDocumentId().isPresent());
        assertEquals(newId, value.getDocumentId().get());
    }

    @Test
    public void can_assign_new_id_for_existing_reference() {
        ReferenceFieldValue value = new ReferenceFieldValue(referenceTypeFoo());
        DocumentId newId = docId("id:ns:foo::wario-time");
        value.assign(newId);
        assertTrue(value.getDocumentId().isPresent());
        assertEquals(newId, value.getDocumentId().get());
    }

    // This is legacy behaviour and does not smell entirely nice.
    @Test
    public void assigning_null_implies_clearing_id() {
        ReferenceFieldValue value = new ReferenceFieldValue(referenceTypeFoo());
        value.assign(null);
        assertFalse(value.getDocumentId().isPresent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void assigning_non_reference_field_value_instance_throws_exception() {
        ReferenceFieldValue value = new ReferenceFieldValue(referenceTypeFoo());
        value.assign("nope!");
    }

    @Test
    public void can_assign_empty_reference_field_value_instance_to_existing_reference() {
        ReferenceFieldValue existing = new ReferenceFieldValue(referenceTypeFoo());
        ReferenceFieldValue newValue = new ReferenceFieldValue(referenceTypeFoo());
        // Logically a no-op, but still worth testing.
        existing.assign(newValue);
        assertEquals(newValue, existing);
    }

    @Test
    public void can_assign_reference_field_value_instance_with_id_to_existing_reference() {
        ReferenceFieldValue existing = new ReferenceFieldValue(referenceTypeFoo());
        ReferenceFieldValue newValue = new ReferenceFieldValue(referenceTypeFoo(), docId("id:ns:foo::toad"));
        existing.assign(newValue);
        assertEquals(newValue, existing);
    }

    @Test
    public void assigning_reference_field_with_different_type_to_existing_reference_throws_exception() {
        ReferenceFieldValue existing = new ReferenceFieldValue(referenceTypeFoo());
        ReferenceFieldValue newValue = new ReferenceFieldValue(referenceTypeBar());
        try {
            existing.assign(newValue);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Can't assign reference of type Reference<bar> to reference of type Reference<foo>",
                    e.getMessage());
        }
    }

    @Test
    public void reference_value_can_be_cleared() {
        ReferenceFieldValue value = new ReferenceFieldValue(referenceTypeFoo(), docId("id:ns:foo::yoshi-egg-feast"));
        value.clear();
        assertFalse(value.getDocumentId().isPresent());
    }

    @Test
    public void references_with_different_type_ids_are_not_equal() {
        ReferenceFieldValue lhs = new ReferenceFieldValue(referenceTypeFoo(), docId("id:ns:foo::toad"));
        ReferenceFieldValue rhs = new ReferenceFieldValue(referenceTypeFooDifferentId(), docId("id:ns:foo::toad"));
        assertNotEquals(lhs, rhs);
    }

    @Test
    public void references_with_different_document_ids_are_not_equal() {
        ReferenceFieldValue lhs = new ReferenceFieldValue(referenceTypeFoo(), docId("id:ns:foo::peach"));
        ReferenceFieldValue rhs = new ReferenceFieldValue(referenceTypeFoo(), docId("id:ns:foo::bowser"));
        assertNotEquals(lhs, rhs);
    }

    @Test
    public void references_with_same_type_and_id_are_equal() {
        ReferenceFieldValue lhs = new ReferenceFieldValue(referenceTypeFoo(), docId("id:ns:foo::toad"));
        ReferenceFieldValue rhs = new ReferenceFieldValue(referenceTypeFoo(), docId("id:ns:foo::toad"));
        assertEquals(lhs, rhs);
    }
    @Test
    public void references_with_same_type_and_no_id_are_equal() {
        ReferenceFieldValue lhs = new ReferenceFieldValue(referenceTypeFoo());
        ReferenceFieldValue rhs = new ReferenceFieldValue(referenceTypeFoo());
        assertEquals(lhs, rhs);
    }

    @Test
    public void hash_code_takes_type_and_id_into_account() {
        ReferenceFieldValue fooField = new ReferenceFieldValue(referenceTypeFoo(), docId("id:ns:foo::toad"));
        ReferenceFieldValue barField = new ReferenceFieldValue(referenceTypeBar(), docId("id:ns:bar::toad"));
        ReferenceFieldValue fooFieldWithDifferentId = new ReferenceFieldValue(referenceTypeFoo(), docId("id:ns:foo::luigi"));

        // ... with a very high probability:
        assertNotEquals(fooField.hashCode(), barField.hashCode());
        assertNotEquals(fooField.hashCode(), fooFieldWithDifferentId.hashCode());
        assertNotEquals(barField.hashCode(), fooFieldWithDifferentId.hashCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void reference_constructor_requires_that_id_has_same_document_type_as_data_type() {
        new ReferenceFieldValue(referenceTypeFoo(), docId("id:ns:bar::mismatch"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void reference_doc_id_setter_requires_that_id_has_same_document_type_as_data_type() {
        ReferenceFieldValue value = new ReferenceFieldValue(referenceTypeFoo());
        value.setDocumentId(docId("id:ns:bar::mismatch"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void assigning_new_id_for_existing_reference_requires_that_id_has_same_document_type_as_data_type() {
        ReferenceFieldValue value = new ReferenceFieldValue(referenceTypeFoo());
        DocumentId newId = docId("id:ns:bar::mama-mia");
        value.assign(newId);
    }

    @Test
    public void exposed_wrapped_value_is_null_for_empty_reference() {
        ReferenceFieldValue nullRef = new ReferenceFieldValue(referenceTypeFoo());
        assertNull(nullRef.getWrappedValue());
    }

    @Test
    public void expose_wrapped_value_is_doc_id_for_non_empty_reference() {
        ReferenceFieldValue idRef = new ReferenceFieldValue(referenceTypeFoo(), docId("id:ns:foo::toad"));
        assertEquals(docId("id:ns:foo::toad"), idRef.getWrappedValue());
    }

    @Test
    public void that_toString_provides_value() {
        assertEquals("Optional.empty", new ReferenceFieldValue(referenceTypeFoo()).toString());
        assertEquals("Optional[id:ns:foo::toad]", new ReferenceFieldValue(referenceTypeFoo(), docId("id:ns:foo::toad")).toString());
    }

}
