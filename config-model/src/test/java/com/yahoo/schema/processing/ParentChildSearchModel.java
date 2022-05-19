// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.schema.DocumentReference;
import com.yahoo.schema.DocumentReferences;
import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.TestableDeployLogger;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.TemporaryImportedField;
import com.yahoo.schema.document.TemporarySDField;

/*
 * Fixture class used for ImportedFieldsResolverTestCase and AdjustPositionSummaryFieldsTestCase.
 */
public class ParentChildSearchModel {

    public Schema parentSchema;
    public Schema childSchema;

    ParentChildSearchModel() {
        parentSchema = createSearch("parent");
        childSchema = createSearch("child");
    }

    protected Schema createSearch(String name) {
        Schema result = new Schema(name, MockApplicationPackage.createEmpty(), new MockFileRegistry(), new TestableDeployLogger(), new TestProperties());
        result.addDocument(new SDDocumentType(name));
        return result;
    }

    protected static TemporarySDField createField(SDDocumentType repo, String name, DataType dataType, String indexingScript) {
        TemporarySDField result = new TemporarySDField(repo, name, dataType);
        result.parseIndexingScript(indexingScript);
        return result;
    }

    @SuppressWarnings("deprecation")
    protected static SDField createRefField(SDDocumentType repo, String parentType, String fieldName) {
        return new TemporarySDField(repo, fieldName, NewDocumentReferenceDataType.forDocumentName(parentType));
    }

    protected static void addRefField(Schema child, Schema parent, String fieldName) {
        SDField refField = createRefField(child.getDocument(), parent.getName(), fieldName);
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
