// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.TemporaryStructuredDataType;
import com.yahoo.searchdefinition.DocumentReference;
import com.yahoo.searchdefinition.DocumentReferences;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.ImportedField;
import com.yahoo.searchdefinition.document.ImportedFields;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.TemporaryImportedField;
import com.yahoo.searchdefinition.document.TemporarySDField;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * @author geirst
 */
public class ImportedFieldsResolverTestCase {

    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void valid_imported_fields_are_resolved() {
        SearchModel model = new SearchModel();
        model.add(new TemporaryImportedField("my_budget", "campaign_ref", "budget")).resolve();

        assertEquals(1, model.importedFields.fields().size());
        ImportedField myBudget = model.importedFields.fields().get("my_budget");
        assertNotNull(myBudget);
        assertEquals("my_budget", myBudget.aliasFieldName());
        assertSame(model.adSearch.getField("campaign_ref"), myBudget.documentReference().documentReferenceField());
        assertSame(model.campaignSearch, myBudget.documentReference().search());
        assertSame(model.campaignSearch.getField("budget"), myBudget.referencedField());
    }

    @Test
    public void resolver_fails_if_document_reference_is_not_found() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("For search 'ad', import field 'my_budget': Imported field is not valid. "
                + "Document reference field 'not_campaign_ref' not found");
        new SearchModel().add(new TemporaryImportedField("my_budget", "not_campaign_ref", "budget")).resolve();
    }

    @Test
    public void resolver_fails_if_referenced_field_is_not_found() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("For search 'ad', import field 'my_budget': Imported field is not valid. "
                + "Field 'not_budget' via document reference field 'campaign_ref' not found");
        new SearchModel().add(new TemporaryImportedField("my_budget", "campaign_ref", "not_budget")).resolve();
    }

    private static class SearchModel {
        public final Search campaignSearch;
        public final Search adSearch;
        public ImportedFields importedFields;
        public SearchModel() {
            ApplicationPackage app = MockApplicationPackage.createEmpty();

            campaignSearch = new Search("campaign", app);
            campaignSearch.addDocument(new SDDocumentType("campaign"));
            campaignSearch.getDocument().addField(new TemporarySDField("budget", DataType.INT));

            adSearch = new Search("ad", app);
            adSearch.addDocument(new SDDocumentType("ad"));
            SDField campaignRefField = new TemporarySDField("campaign_ref", ReferenceDataType.createWithInferredId(TemporaryStructuredDataType.create("campaign")));
            adSearch.getDocument().addField(campaignRefField);
            adSearch.getDocument().setDocumentReferences(new DocumentReferences(ImmutableMap.of(campaignRefField.getName(),
                    new DocumentReference(campaignRefField, campaignSearch))));
        }
        public SearchModel add(TemporaryImportedField importedField) {
            adSearch.temporaryImportedFields().get().add(importedField);
            return this;
        }
        public void resolve() {
            assertNotNull(adSearch.temporaryImportedFields().get());
            assertFalse(adSearch.importedFields().isPresent());
            new ImportedFieldsResolver(adSearch, null, null, null).process();
            assertFalse(adSearch.temporaryImportedFields().isPresent());
            assertNotNull(adSearch.importedFields().get());
            importedFields = adSearch.importedFields().get();
        }
    }

}
