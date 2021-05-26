// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.document.DataType;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.TemporaryImportedField;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ImportedFieldsEnumeratorTest {

    @Test
    public void imported_fields_are_enumerated_and_copied_from_correct_search_instance() {
        String PARENT = "parent";
        Search parentSearch = new Search(PARENT);
        SDDocumentType parentDocument = new SDDocumentType(PARENT, parentSearch);
        var parentField = new SDField("their_field", DataType.INT);
        AttributeUtils.addAttributeAspect(parentField);
        parentDocument.addField(parentField);
        parentSearch.addDocument(parentDocument);

        String FOO = "foo";
        Search fooSearch = new Search(FOO);
        SDField fooRefToParent = new SDField(
                "foo_ref", ReferenceDataType.createWithInferredId(parentDocument.getDocumentType()));
        AttributeUtils.addAttributeAspect(fooRefToParent);
        var fooImports = fooSearch.temporaryImportedFields().get();
        fooImports.add(new TemporaryImportedField("my_first_import", "foo_ref", "their_field"));
        fooImports.add(new TemporaryImportedField("my_second_import", "foo_ref", "their_field"));
        SDDocumentType fooDocument = new SDDocumentType(FOO, fooSearch);
        fooSearch.addDocument(fooDocument);

        String BAR = "bar";
        Search barSearch = new Search(BAR);
        SDField barRefToParent = new SDField(
                "bar_ref", ReferenceDataType.createWithInferredId(parentDocument.getDocumentType()));
        AttributeUtils.addAttributeAspect(barRefToParent);
        var barImports = barSearch.temporaryImportedFields().get();
        barImports.add(new TemporaryImportedField("my_cool_import", "my_ref", "their_field"));
        SDDocumentType barDocument = new SDDocumentType(BAR, barSearch);
        barSearch.addDocument(barDocument);

        var enumerator = new ImportedFieldsEnumerator(List.of(parentSearch, fooSearch, barSearch));

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
