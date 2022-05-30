// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.schema.DocumentReference;
import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.TestableDeployLogger;
import com.yahoo.schema.document.ImportedField;
import com.yahoo.schema.document.ImportedFields;
import com.yahoo.schema.document.ImportedSimpleField;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
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

    @SuppressWarnings("deprecation")
    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void throws_exception_if_type_of_document_field_does_not_match_summary_field() {
        Schema schema = createSearchWithDocument(DOCUMENT_NAME);
        schema.setImportedFields(createSingleImportedField(IMPORTED_FIELD_NAME, DataType.INT));
        schema.addSummary(createDocumentSummary(IMPORTED_FIELD_NAME, DataType.STRING, schema));

        ValidateFieldTypes validator = new ValidateFieldTypes(schema, null, null, null);
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "For schema '" + DOCUMENT_NAME + "', field '" + IMPORTED_FIELD_NAME + "': Incompatible types. " +
                "Expected int for summary field '" + IMPORTED_FIELD_NAME + "', got string.");
        validator.process(true, false);
    }

    private static Schema createSearch(String documentType) {
        return new Schema(documentType,
                          MockApplicationPackage.createEmpty(),
                          new MockFileRegistry(),
                          new TestableDeployLogger(),
                          new TestProperties());
    }

    private static Schema createSearchWithDocument(String documentName) {
        Schema schema = createSearch(documentName);
        SDDocumentType document = new SDDocumentType(documentName, schema);
        schema.addDocument(document);
        return schema;
    }

    private static ImportedFields createSingleImportedField(String fieldName, DataType dataType) {
        Schema targetSchema = createSearchWithDocument("target_doc");
        SDField targetField = new SDField(targetSchema.getDocument(), "target_field", dataType);
        DocumentReference documentReference = new DocumentReference(new Field("reference_field"), targetSchema);
        ImportedField importedField = new ImportedSimpleField(fieldName, documentReference, targetField);
        return new ImportedFields(Collections.singletonMap(fieldName, importedField));
    }

    private static DocumentSummary createDocumentSummary(String fieldName, DataType dataType, Schema schema) {
        DocumentSummary summary = new DocumentSummary("mysummary", schema);
        summary.add(new SummaryField(fieldName, dataType));
        return summary;
    }

}
