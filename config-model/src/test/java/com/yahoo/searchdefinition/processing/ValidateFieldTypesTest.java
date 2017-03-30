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
    private static final String DOCUMENT_NAME = "mydoc";

    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void throws_exception_if_type_of_document_field_does_not_match_summary_field() {
        Search search = createDocumentWithImportedFieldInDocumentSummary();
        ValidateFieldTypes validator = new ValidateFieldTypes(search, null, null, null);
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "For search '" + DOCUMENT_NAME + "', field '" + IMPORTED_FIELD_NAME + "': Incompatible types. " +
                "Expected int for summary field '" + IMPORTED_FIELD_NAME + "', got string.");
        validator.process();
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
        DocumentSummary summary = new DocumentSummary("mysummary");
        summary.add(new SummaryField(IMPORTED_FIELD_NAME, DataType.STRING));
        search.addSummary(summary);
        return search;
    }
}
