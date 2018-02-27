// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.document.DataType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * @author geirst
 */
public class ImportedFieldsInSummaryValidatorTestCase {

    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void validator_fails_if_imported_predicate_field_is_used_in_document_summary() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("For search 'child', document summary 'my_summary', " +
                "imported summary field 'my_predicate_field': Is of type predicate. Not supported in document summaries");
        new SearchModel()
                .addImportedField("my_predicate_field", "ref", "predicate_field")
                .addSummaryField("my_predicate_field", DataType.PREDICATE)
                .resolve();
    }

    private static class SearchModel extends ImportedFieldsResolverTestCase.SearchModel {

        public SearchModel() {
            super();
        }

        public void resolve() {
            super.resolve();
            new ImportedFieldsInSummayValidator(childSearch, null, null, null).process(true);
        }

    }

}

