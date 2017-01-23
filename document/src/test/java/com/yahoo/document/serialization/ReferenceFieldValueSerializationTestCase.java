// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.Field;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.datatypes.ReferenceFieldValue;
import org.junit.Test;

public class ReferenceFieldValueSerializationTestCase {

    static class Fixture {
        final TestDocumentFactory documentFactory;
        final static String REF_TARGET_DOC_TYPE_NAME = "ref_target_type";
        final static String REF_SOURCE_DOC_TYPE_NAME = "ref_src_type";
        final static String SOURCE_REF_FIELD_NAME = "ref_field";

        Fixture() {
            final DocumentTypeManager typeManager = new DocumentTypeManager();
            final DocumentType targetType = new DocumentType(REF_TARGET_DOC_TYPE_NAME);
            // Since we're programmatically referring to a specific target DocumentType, we have to
            // create it before we create the source document type containing a reference to it.
            typeManager.register(targetType);
            final DocumentType sourceType = createReferenceSourceDocumentType(typeManager, REF_SOURCE_DOC_TYPE_NAME, targetType.getName());
            typeManager.register(sourceType);

            this.documentFactory = new TestDocumentFactory(typeManager, sourceType, "id:ns:" + REF_SOURCE_DOC_TYPE_NAME + "::foo");
        }

        DocumentType createReferenceSourceDocumentType(DocumentTypeManager typeManager, String docTypeName, String targetName) {
            final DocumentType type = new DocumentType(docTypeName);
            type.addField(new Field(SOURCE_REF_FIELD_NAME, new ReferenceDataType(
                    typeManager.getDocumentType(targetName),
                    targetName.hashCode())));
            return type;
        }

        ReferenceFieldValue createEmptyReferenceFieldValue() {
            final DocumentType docTypeWithRefs = documentFactory.typeManager().getDocumentType(REF_SOURCE_DOC_TYPE_NAME);
            return (ReferenceFieldValue)docTypeWithRefs.getField(SOURCE_REF_FIELD_NAME).getDataType().createFieldValue();
        }

        ReferenceFieldValue createReferenceFieldValueWithId(DocumentId id) {
            final ReferenceFieldValue value = createEmptyReferenceFieldValue();
            value.setDocumentId(id);
            return value;
        }
    }

    @Test
    public void empty_reference_field_can_be_roundtrip_serialized() {
        final Fixture fixture = new Fixture();
        SerializationTestUtils.assertFieldInDocumentSerialization(
                fixture.documentFactory, Fixture.SOURCE_REF_FIELD_NAME, fixture.createEmptyReferenceFieldValue());
    }

    @Test
    public void non_empty_reference_field_can_be_roundtrip_serialized() {
        final Fixture fixture = new Fixture();
        SerializationTestUtils.assertFieldInDocumentSerialization(
                fixture.documentFactory, Fixture.SOURCE_REF_FIELD_NAME,
                fixture.createReferenceFieldValueWithId(new DocumentId("id:ns:" + Fixture.REF_TARGET_DOC_TYPE_NAME + "::bar")));
    }

    // TODO test cross-language serialization once we've got the C++ version of the types
}
