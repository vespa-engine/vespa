// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.TemporaryStructuredDataType;
import com.yahoo.searchdefinition.DocumentReference;
import com.yahoo.searchdefinition.DocumentReferences;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.derived.TestableDeployLogger;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.TemporaryImportedField;
import com.yahoo.searchdefinition.document.TemporarySDField;

/*
 * Fixture class used for ImportedFieldsResolverTestCase and AdjustPositionSummaryFieldsTestCase.
 */
public class ParentChildSearchModel {
    private final ApplicationPackage app = MockApplicationPackage.createEmpty();
    public Search parentSearch;
    public Search childSearch;

    ParentChildSearchModel() {
        parentSearch = createSearch("parent");
        childSearch = createSearch("child");
    }

    protected Search createSearch(String name) {
        Search result = new Search(name, app, new MockFileRegistry(), new TestableDeployLogger(), new TestProperties());
        result.addDocument(new SDDocumentType(name));
        return result;
    }

    protected static TemporarySDField createField(String name, DataType dataType, String indexingScript) {
        TemporarySDField result = new TemporarySDField(name, dataType);
        result.parseIndexingScript(indexingScript);
        return result;
    }

    protected static SDField createRefField(String parentType, String fieldName) {
        return new TemporarySDField(fieldName, ReferenceDataType.createWithInferredId(TemporaryStructuredDataType.create(parentType)));
    }

    protected static void addRefField(Search child, Search parent, String fieldName) {
        SDField refField = createRefField(parent.getName(), fieldName);
        child.getDocument().addField(refField);
        child.getDocument().setDocumentReferences(new DocumentReferences(ImmutableMap.of(refField.getName(),
                new DocumentReference(refField, parent))));
    }

    protected ParentChildSearchModel addImportedField(String fieldName, String referenceFieldName, String targetFieldName) {
        return addImportedField(childSearch, fieldName, referenceFieldName, targetFieldName);
    }

    protected ParentChildSearchModel addImportedField(Search search, String fieldName, String referenceFieldName, String targetFieldName) {
        search.temporaryImportedFields().get().add(new TemporaryImportedField(fieldName, referenceFieldName, targetFieldName));
        return this;
    }
}
