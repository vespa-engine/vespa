// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.Field;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.datatypes.ReferenceFieldValue;
import org.junit.Test;

import java.io.IOException;

/**
 * @author vekterli
 * @since 6.65
 */
public class ReferenceFieldValueSerializationTestCase {

    static class Fixture {
        final TestDocumentFactory documentFactory;
        // Note: these must match their C++ serialization test counterparts.
        final static String REF_TARGET_DOC_TYPE_NAME = "my_doctype";
        final static String REF_SOURCE_DOC_TYPE_NAME = "doc_with_ref";
        final static int REF_TYPE_ID = 789;
        final static String SOURCE_REF_FIELD_NAME = "ref_field";
        final static String CROSS_LANGUAGE_PATH = "src/test/resources/reference/";

        Fixture() {
            final DocumentTypeManager typeManager = new DocumentTypeManager();
            final DocumentType targetType = new DocumentType(REF_TARGET_DOC_TYPE_NAME);
            // Since we're programmatically referring to a specific target DocumentType, we have to
            // create it before we create the source document type containing a reference to it.
            typeManager.register(targetType);
            final DocumentType sourceType = createReferenceSourceDocumentType(typeManager, REF_SOURCE_DOC_TYPE_NAME, targetType.getName());
            typeManager.register(sourceType);

            this.documentFactory = new TestDocumentFactory(typeManager, sourceType, "id:test:" + REF_SOURCE_DOC_TYPE_NAME + "::foo");
        }

        DocumentType createReferenceSourceDocumentType(DocumentTypeManager typeManager, String docTypeName, String targetName) {
            final DocumentType type = new DocumentType(docTypeName);
            type.addField(new Field(SOURCE_REF_FIELD_NAME, new ReferenceDataType(
                    typeManager.getDocumentType(targetName),
                    REF_TYPE_ID)));
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

        Document createDocumentWithReference(ReferenceFieldValue refValue) {
            final Document document = documentFactory.createDocument();
            document.setFieldValue(Fixture.SOURCE_REF_FIELD_NAME, refValue);
            return document;
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

    @Test
    public void empty_reference_serialization_matches_cpp() throws IOException {
        final Fixture fixture = new Fixture();
        final Document document = fixture.createDocumentWithReference(fixture.createEmptyReferenceFieldValue());

        SerializationTestUtils.assertSerializationMatchesCpp(
                Fixture.CROSS_LANGUAGE_PATH, "empty_reference", document, fixture.documentFactory);
    }

    @Test
    public void reference_with_id_serialization_matches_cpp() throws IOException {
        final Fixture fixture = new Fixture();
        final Document document = fixture.createDocumentWithReference(fixture.createReferenceFieldValueWithId(
                new DocumentId("id:ns:" + Fixture.REF_TARGET_DOC_TYPE_NAME + "::bar")));

        SerializationTestUtils.assertSerializationMatchesCpp(
                Fixture.CROSS_LANGUAGE_PATH, "reference_with_id", document, fixture.documentFactory);
    }

}
