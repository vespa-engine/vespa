// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.TemporaryStructuredDataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.searchdefinition.DocumentReference;
import com.yahoo.searchdefinition.DocumentReferences;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.ImportedField;
import com.yahoo.searchdefinition.document.ImportedFields;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.TemporaryImportedField;
import com.yahoo.searchdefinition.document.TemporarySDField;
import com.yahoo.tensor.TensorType;
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
        model.add(new TemporaryImportedField("my_attribute_field", "ref", "attribute_field")).resolve();

        assertEquals(1, model.importedFields.fields().size());
        ImportedField myField = model.importedFields.fields().get("my_attribute_field");
        assertNotNull(myField);
        assertEquals("my_attribute_field", myField.fieldName());
        assertSame(model.childSearch.getField("ref"), myField.reference().referenceField());
        assertSame(model.parentSearch, myField.reference().targetSearch());
        assertSame(model.parentSearch.getField("attribute_field"), myField.targetField());
    }

    @Test
    public void resolver_fails_if_document_reference_is_not_found() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("For search 'child', import field 'my_attribute_field': "
                + "Reference field 'not_ref' not found");
        new SearchModel().add(new TemporaryImportedField("my_attribute_field", "not_ref", "budget")).resolve();
    }

    @Test
    public void resolver_fails_if_referenced_field_is_not_found() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("For search 'child', import field 'my_attribute_field': "
                + "Field 'not_existing' via reference field 'ref': Not found");
        new SearchModel().add(new TemporaryImportedField("my_attribute_field", "ref", "not_existing")).resolve();
    }

    @Test
    public void resolver_fails_if_imported_field_is_not_an_attribute() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("For search 'child', import field 'my_not_attribute': "
                + "Field 'not_attribute' via reference field 'ref': Is not an attribute");
        new SearchModel().add(new TemporaryImportedField("my_not_attribute", "ref", "not_attribute")).resolve();
    }

    @Test
    public void resolver_fails_if_imported_field_is_indexing() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "For search 'child', import field 'my_attribute_and_index': " +
                        "Field 'attribute_and_index' via reference field 'ref': Index field not supported");
        new SearchModel()
                .add(new TemporaryImportedField("my_attribute_and_index", "ref", "attribute_and_index"))
                .resolve();
    }

    @Test
    public void resolver_fails_if_imported_field_is_tensor_type() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "For search 'child', import field 'my_tensor_field': " +
                        "Field 'tensor_field' via reference field 'ref': Type 'tensor' not supported");
        new SearchModel()
                .add(new TemporaryImportedField("my_tensor_field", "ref", "tensor_field"))
                .resolve();
    }

    private static class SearchModel {
        public final Search parentSearch;
        public final Search childSearch;
        public ImportedFields importedFields;
        public SearchModel() {
            ApplicationPackage app = MockApplicationPackage.createEmpty();

            parentSearch = new Search("parent", app);
            parentSearch.addDocument(new SDDocumentType("parent"));
            parentSearch.getDocument().addField(createField("attribute_field", DataType.INT, "{ attribute }"));
            parentSearch.getDocument().addField(createField("attribute_and_index", DataType.INT, "{ attribute | index }"));
            parentSearch.getDocument().addField(new TemporarySDField("not_attribute", DataType.INT));
            parentSearch.getDocument().addField(createField("tensor_field", new TensorDataType(TensorType.fromSpec("tensor(x[])")), "{ attribute }"));

            childSearch = new Search("child", app);
            childSearch.addDocument(new SDDocumentType("child"));
            SDField parentRefField = new TemporarySDField("ref", ReferenceDataType.createWithInferredId(TemporaryStructuredDataType.create("parent")));
            childSearch.getDocument().addField(parentRefField);
            childSearch.getDocument().setDocumentReferences(new DocumentReferences(ImmutableMap.of(parentRefField.getName(),
                    new DocumentReference(parentRefField, parentSearch))));
        }
        private TemporarySDField createField(String name, DataType dataType, String indexingScript) {
            TemporarySDField result = new TemporarySDField(name, dataType);
            result.parseIndexingScript(indexingScript);
            return result;
        }
        public SearchModel add(TemporaryImportedField importedField) {
            childSearch.temporaryImportedFields().get().add(importedField);
            return this;
        }
        public void resolve() {
            assertNotNull(childSearch.temporaryImportedFields().get());
            assertFalse(childSearch.importedFields().isPresent());
            new ImportedFieldsResolver(childSearch, null, null, null).process();
            assertFalse(childSearch.temporaryImportedFields().isPresent());
            assertNotNull(childSearch.importedFields().get());
            importedFields = childSearch.importedFields().get();
        }
    }

}
