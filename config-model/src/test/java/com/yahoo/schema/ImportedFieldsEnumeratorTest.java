// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.TemporaryImportedField;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ImportedFieldsEnumeratorTest {

    @Test
    void imported_fields_are_enumerated_and_copied_from_correct_search_instance() {
        String PARENT = "parent";
        Schema parentSchema = new Schema(PARENT, MockApplicationPackage.createEmpty());
        SDDocumentType parentDocument = new SDDocumentType(PARENT, parentSchema);
        var parentField = new SDField(parentDocument, "their_field", DataType.INT);
        AttributeUtils.addAttributeAspect(parentField);
        parentDocument.addField(parentField);
        parentSchema.addDocument(parentDocument);

        String FOO = "foo";
        Schema fooSchema = new Schema(FOO, MockApplicationPackage.createEmpty());
        /*
        SDField fooRefToParent = new SDField(
                "foo_ref", NewDocumentReferenceDataType.createWithInferredId(parentDocument.getDocumentType()));
        AttributeUtils.addAttributeAspect(fooRefToParent);
        */
        var fooImports = fooSchema.temporaryImportedFields().get();
        fooImports.add(new TemporaryImportedField("my_first_import", "foo_ref", "their_field"));
        fooImports.add(new TemporaryImportedField("my_second_import", "foo_ref", "their_field"));
        SDDocumentType fooDocument = new SDDocumentType(FOO, fooSchema);
        fooSchema.addDocument(fooDocument);

        String BAR = "bar";
        Schema barSchema = new Schema(BAR, MockApplicationPackage.createEmpty());
        /*
        SDField barRefToParent = new SDField(
                "bar_ref", NewDocumentReferenceDataType.createWithInferredId(parentDocument.getDocumentType()));
        AttributeUtils.addAttributeAspect(barRefToParent);
        */
        var barImports = barSchema.temporaryImportedFields().get();
        barImports.add(new TemporaryImportedField("my_cool_import", "my_ref", "their_field"));
        SDDocumentType barDocument = new SDDocumentType(BAR, barSchema);
        barSchema.addDocument(barDocument);

        var enumerator = new ImportedFieldsEnumerator(List.of(parentSchema, fooSchema, barSchema));

        enumerator.enumerateImportedFields(parentDocument);
        assertImportedFieldsAre(parentDocument, List.of()); // No imported fields in parent

        enumerator.enumerateImportedFields(fooDocument);
        assertImportedFieldsAre(fooDocument, List.of("my_first_import", "my_second_import"));

        enumerator.enumerateImportedFields(barDocument);
        assertImportedFieldsAre(barDocument, List.of("my_cool_import"));
    }

    private void assertImportedFieldsAre(SDDocumentType documentType, List<String> expectedNames) {
        assertNotNull(documentType.getTemporaryImportedFields());
        var actualNames = documentType.getTemporaryImportedFields().fields().keySet();
        var expectedNameSet = new HashSet<>(expectedNames);
        assertEquals(expectedNameSet, actualNames);
    }

}
