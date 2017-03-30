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
        Search search = createDocumentWithImportedFieldInDocumentSummary();
        AddAttributeTransformToSummaryOfImportedFields processor = new AddAttributeTransformToSummaryOfImportedFields(
                search,null,null,null);
        processor.process();
        SummaryField summaryField = search.getSummaries().get(SUMMARY_NAME).getSummaryField(IMPORTED_FIELD_NAME);
        SummaryTransform actualTransform = summaryField.getTransform();
        assertEquals(SummaryTransform.ATTRIBUTE, actualTransform);
    }

    private static Search createDocumentWithImportedFieldInDocumentSummary() {
        // Create search with a single imported field
        Search search = new Search(DOCUMENT_NAME, MockApplicationPackage.createEmpty());
        SDDocumentType document = new SDDocumentType("mydoc", search);
        search.addDocument(document);
        Search targetDocument = new Search("otherdoc", MockApplicationPackage.createEmpty());
        SDField targetField = new SDField("myfield", DataType.INT);
        DocumentReference documentReference = new DocumentReference(new Field("reference_field"), targetDocument);
        ImportedField importedField = new ImportedField(IMPORTED_FIELD_NAME, documentReference, targetField);
        search.setImportedFields(new ImportedFields(Collections.singletonMap(IMPORTED_FIELD_NAME, importedField)));

        // Create document summary
        DocumentSummary summary = new DocumentSummary(SUMMARY_NAME);
        summary.add(new SummaryField(IMPORTED_FIELD_NAME, DataType.INT));
        search.addSummary(summary);
        return search;
    }

}
