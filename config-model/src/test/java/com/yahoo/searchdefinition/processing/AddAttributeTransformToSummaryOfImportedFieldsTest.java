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
import com.yahoo.vespa.documentmodel.SummaryTransform;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * @author bjorncs
 */
public class AddAttributeTransformToSummaryOfImportedFieldsTest {

    private static final String IMPORTED_FIELD_NAME = "imported_myfield";
    private static final String DOCUMENT_NAME = "mydoc";
    private static final String SUMMARY_NAME = "mysummary";

    @Test
    public void attribute_summary_transform_applied_to_summary_field_of_imported_field() {
        Search search = createSearchWithDocument(DOCUMENT_NAME);
        search.setImportedFields(createSingleImportedField(IMPORTED_FIELD_NAME));
        search.addSummary(createDocumentSummary(IMPORTED_FIELD_NAME));

        AddAttributeTransformToSummaryOfImportedFields processor = new AddAttributeTransformToSummaryOfImportedFields(
                search,null,null,null);
        processor.process(true);
        SummaryField summaryField = search.getSummaries().get(SUMMARY_NAME).getSummaryField(IMPORTED_FIELD_NAME);
        SummaryTransform actualTransform = summaryField.getTransform();
        assertEquals(SummaryTransform.ATTRIBUTE, actualTransform);
    }

    private static Search createSearchWithDocument(String documentName) {
        Search search = new Search(documentName, MockApplicationPackage.createEmpty());
        SDDocumentType document = new SDDocumentType(documentName, search);
        search.addDocument(document);
        return search;
    }

    private static ImportedFields createSingleImportedField(String fieldName) {
        Search targetSearch = new Search("target_doc", MockApplicationPackage.createEmpty());
        SDField targetField = new SDField("target_field", DataType.INT);
        DocumentReference documentReference = new DocumentReference(new Field("reference_field"), targetSearch);
        ImportedField importedField = new ImportedField(fieldName, documentReference, targetField);
        return new ImportedFields(Collections.singletonMap(fieldName, importedField));
    }

    private static DocumentSummary createDocumentSummary(String fieldName) {
        DocumentSummary summary = new DocumentSummary("mysummary");
        summary.add(new SummaryField(fieldName, DataType.INT));
        return summary;
    }

}
