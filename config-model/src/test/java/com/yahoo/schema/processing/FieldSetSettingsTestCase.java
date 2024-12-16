// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.schema.derived.TestableDeployLogger;
import org.junit.jupiter.api.Test;

import static com.yahoo.schema.ApplicationBuilder.createFromStrings;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class FieldSetSettingsTestCase {

    @Test
    public void legalFieldTypeMix() {
        assertDoesNotThrow(() -> createFromStrings(new BaseDeployLogger(), childSd("fieldset default { fields: ci,pi }"), parentSd()));
        assertDoesNotThrow(() -> createFromStrings(new BaseDeployLogger(), childSd("fieldset default { fields: ct,pt }"), parentSd()));
    }

    @Test
    public void warnableFieldTypeMix() {
        var logger = new TestableDeployLogger();
        assertDoesNotThrow(() -> createFromStrings(logger, childSd("fieldset default { fields: ci,ps }"), parentSd()));
        assertArrayEquals(new String[]{
                "For schema 'child', field 'ps': " +
                        "The matching settings for the fields in fieldset 'default' are inconsistent (explicitly or because of field type). " +
                        "This may lead to recall and ranking issues. See https://docs.vespa.ai/en/reference/schema-reference.html#fieldset",
                "For schema 'child', field 'ps': " +
                        "The normalization settings for the fields in fieldset 'default' are inconsistent (explicitly or because of field type). " +
                        "This may lead to recall and ranking issues. See https://docs.vespa.ai/en/reference/schema-reference.html#fieldset"},
                logger.warnings.toArray());
    }

    @Test
    public void illegalFieldTypeMix() {
        var logger = new TestableDeployLogger();
        assertDoesNotThrow(() -> createFromStrings(logger, childSd( "fieldset default { fields: ci, pt }"), parentSd()));
        assertArrayEquals(new String[]{"For schema 'child', fieldset 'default': Tensor fields ['pt'] cannot be mixed with non-tensor fields ['ci'] in the same fieldset. " +
                "See https://docs.vespa.ai/en/reference/schema-reference.html#fieldset"}, logger.warnings.toArray());
    }


    private static String childSd(String fieldSet) {
        return """
                  schema child {
                    document child {
                      field ci type int {
                        indexing: attribute
                      }
                      field cs type string {
                        indexing: attribute
                      }
                      field ct type tensor(x[2]) {
                        indexing: attribute
                      }
                      field parent_ref type reference<parent> {
                        indexing: attribute
                       }
                    }
                    import field parent_ref.pi as pi { }
                    import field parent_ref.ps as ps { }
                    import field parent_ref.pt as pt { }
                  """ + fieldSet + """
                  }
                """;
    }
    private static String parentSd() {
        return """
                schema parent {
                  document parent {
                    field pi type int {
                      indexing: attribute
                    }
                    field ps type string {
                      indexing: attribute
                    }
                    field pt type tensor(x[2]) {
                      indexing: attribute
                    }
                  }
                }
                """;
    }
}
