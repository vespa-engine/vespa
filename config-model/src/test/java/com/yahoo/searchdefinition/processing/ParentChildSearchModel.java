// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.TemporaryStructuredDataType;
import com.yahoo.searchdefinition.Application;
import com.yahoo.searchdefinition.DocumentReference;
import com.yahoo.searchdefinition.DocumentReferences;
import com.yahoo.searchdefinition.Schema;
import com.yahoo.searchdefinition.derived.TestableDeployLogger;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.TemporaryImportedField;
import com.yahoo.searchdefinition.document.TemporarySDField;

/*
 * Fixture class used for ImportedFieldsResolverTestCase and AdjustPositionSummaryFieldsTestCase.
 */
public class ParentChildSearchModel {

    private final Application application = new Application(MockApplicationPackage.createEmpty());
    public Schema parentSchema;
    public Schema childSchema;

    ParentChildSearchModel() {
        parentSchema = createSearch("parent");
        childSchema = createSearch("child");
    }

    protected Schema createSearch(String name) {
        Schema result = new Schema(name, application, new MockFileRegistry(), new TestableDeployLogger(), new TestProperties());
        result.addDocument(new SDDocumentType(name));
        return result;
    }

    protected static TemporarySDField createField(String name, DataType dataType, String indexingScript) {
        TemporarySDField result = new TemporarySDField(name, dataType);
        result.parseIndexingScript(indexingScript);
        return result;
    }

    @SuppressWarnings("deprecation")
    protected static SDField createRefField(String parentType, String fieldName) {
        return new TemporarySDField(fieldName, ReferenceDataType.createWithInferredId(TemporaryStructuredDataType.create(parentType)));
    }

    protected static void addRefField(Schema child, Schema parent, String fieldName) {
        SDField refField = createRefField(parent.getName(), fieldName);
        child.getDocument().addField(refField);
        child.getDocument().setDocumentReferences(new DocumentReferences(ImmutableMap.of(refField.getName(),
                new DocumentReference(refField, parent))));
    }

    protected ParentChildSearchModel addImportedField(String fieldName, String referenceFieldName, String targetFieldName) {
        return addImportedField(childSchema, fieldName, referenceFieldName, targetFieldName);
    }

    protected ParentChildSearchModel addImportedField(Schema schema, String fieldName, String referenceFieldName, String targetFieldName) {
        schema.temporaryImportedFields().get().add(new TemporaryImportedField(fieldName, referenceFieldName, targetFieldName));
        return this;
    }
}
