// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.searchdefinition.DocumentReference;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.ImportedField;
import com.yahoo.searchdefinition.document.ImportedFields;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Collections;

/**
 * @author bjorncs
 */
public class ValidateFieldTypesTest {

    private static final String IMPORTED_FIELD_NAME = "imported_myfield";
    private static final String DOCUMENT_NAME = "my_doc";

    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void throws_exception_if_type_of_document_field_does_not_match_summary_field() {
        Search search = createSearchWithDocument(DOCUMENT_NAME);
        search.setImportedFields(createSingleImportedField(IMPORTED_FIELD_NAME, DataType.INT));
        search.addSummary(createDocumentSummary(IMPORTED_FIELD_NAME, DataType.STRING));

        ValidateFieldTypes validator = new ValidateFieldTypes(search, null, null, null);
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "For search '" + DOCUMENT_NAME + "', field '" + IMPORTED_FIELD_NAME + "': Incompatible types. " +
                "Expected int for summary field '" + IMPORTED_FIELD_NAME + "', got string.");
        validator.process(true);
    }

    private static Search createSearchWithDocument(String documentName) {
        Search search = new Search(documentName, MockApplicationPackage.createEmpty());
        SDDocumentType document = new SDDocumentType(documentName, search);
        search.addDocument(document);
        return search;
    }

    private static ImportedFields createSingleImportedField(String fieldName, DataType dataType) {
        Search targetSearch = new Search("target_doc", MockApplicationPackage.createEmpty());
        SDField targetField = new SDField("target_field", dataType);
        DocumentReference documentReference = new DocumentReference(new Field("reference_field"), targetSearch);
        ImportedField importedField = new ImportedField(fieldName, documentReference, targetField);
        return new ImportedFields(Collections.singletonMap(fieldName, importedField));
    }

    private static DocumentSummary createDocumentSummary(String fieldName, DataType dataType) {
        DocumentSummary summary = new DocumentSummary("mysummary");
        summary.add(new SummaryField(fieldName, dataType));
        return summary;
    }

}
