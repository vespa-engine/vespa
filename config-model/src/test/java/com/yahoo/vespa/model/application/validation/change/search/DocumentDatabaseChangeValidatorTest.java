// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change.search;

import com.yahoo.vespa.model.application.validation.ValidationOverrides;
import com.yahoo.vespa.model.application.validation.change.VespaConfigChangeAction;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.yahoo.vespa.model.application.validation.change.ConfigChangeTestUtils.newRestartAction;
import static com.yahoo.vespa.model.application.validation.change.ConfigChangeTestUtils.newRefeedAction;

public class DocumentDatabaseChangeValidatorTest {

    private static class Fixture extends ContentClusterFixture {
        DocumentDatabaseChangeValidator validator;

        public Fixture(String currentSd, String nextSd) throws Exception {
            super(currentSd, nextSd);
            validator = new DocumentDatabaseChangeValidator(currentDb(), currentDocType(), nextDb(), nextDocType());
        }

        @Override
        public List<VespaConfigChangeAction> validate() {
            return validator.validate(ValidationOverrides.empty());
        }

    }

    @Test
    public void requireThatAttributeIndexAndDocumentTypeChangesAreDiscovered() throws Exception {
        Fixture f = new Fixture("field f1 type string { indexing: summary } " +
                "field f2 type string { indexing: summary } " +
                "field f3 type int { indexing: summary }",
                "field f1 type string { indexing: attribute | summary } " +
                "field f2 type string { indexing: index | summary } " +
                "field f3 type string { indexing: summary }");
        f.assertValidation(Arrays.asList(
                newRestartAction("Field 'f1' changed: add attribute aspect"),
                newRefeedAction("indexing-change",
                                ValidationOverrides.empty(),
                                "Field 'f2' changed: add index aspect, indexing script: '{ input f2 | summary f2; }' -> " +
                                "'{ input f2 | tokenize normalize stem:\"SHORTEST\" | index f2 | summary f2; }'"),
                newRefeedAction("field-type-change",
                                ValidationOverrides.empty(),
                                "Field 'f3' changed: data type: 'int' -> 'string'")));
    }

    @Test
    public void requireThatRemovingAttributeAspectFromIndexFieldIsOk() throws Exception {
        Fixture f = new Fixture("field f1 type string { indexing: index | attribute }",
                "field f1 type string { indexing: index }");
        f.assertValidation();
    }

}
