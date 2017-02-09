// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.document.ImportedField;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;
import static com.yahoo.searchdefinition.TestUtils.joinLines;
import static org.junit.Assert.assertNotNull;

/**
 * @author geirst
 */
public class ImportedFieldsTestCase {

    @Test
    public void fields_can_be_imported_from_referenced_document_types() throws ParseException {
       Search search = buildAdSearch(joinLines(
               "search ad {",
               "  document ad {",
               "    field campaign_ref type reference<campaign> {}",
               "    field person_ref type reference<person> {}",
               "  }",
               "  import field campaign_ref.budget as my_budget {}",
               "  import field person_ref.name as my_name {}",
                "}"));
        assertEquals(2, search.importedFields().get().fields().size());
        assertSearchContainsImportedField("my_budget", "campaign_ref", "campaign", "budget", search);
        assertSearchContainsImportedField("my_name", "person_ref", "person", "name", search);
    }

    @Test
    public void field_can_be_imported_from_self_reference() throws ParseException {
        Search search = buildAdSearch(joinLines("search ad {",
                "  document ad {",
                "    field title type string { indexing: attribute }",
                "    field self_ref type reference<ad> {}",
                "  }",
                "  import field self_ref.title as my_title {}",
                "}"));
        assertEquals(1, search.importedFields().get().fields().size());
        assertSearchContainsImportedField("my_title", "self_ref", "ad", "title", search);
    }

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void field_reference_spec_must_include_dot() throws ParseException {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Illegal field reference spec 'campaignrefbudget': Does not include a single '.'");
        buildAdSearch(joinLines(
                "search ad {",
                "  document ad {}",
                "  import field campaignrefbudget as budget {}",
                "}"));
    }

    private static Search buildAdSearch(String sdContent) throws ParseException {
        SearchBuilder builder = new SearchBuilder();
        builder.importString(joinLines("search campaign {",
                "  document campaign {",
                "    field budget type int { indexing: attribute }",
                "  }",
                "}"));
        builder.importString(joinLines("search person {",
                "  document person {",
                "    field name type string { indexing: attribute }",
                "  }",
                "}"));
        builder.importString(sdContent);
        builder.build();
        return builder.getSearch("ad");
    }

    private static void assertSearchContainsImportedField(String aliasFieldName,
                                                          String documentReferenceFieldName,
                                                          String documentReferenceType,
                                                          String foreignFieldName,
                                                          Search search) {
        ImportedField importedField = search.importedFields().get().fields().get(aliasFieldName);
        assertNotNull(importedField);
        assertEquals(aliasFieldName, importedField.aliasFieldName());
        assertEquals(documentReferenceFieldName, importedField.documentReference().documentReferenceField().getName());
        assertEquals(documentReferenceType, importedField.documentReference().search().getName());
        assertEquals(foreignFieldName, importedField.referencedField().getName());
    }
}
