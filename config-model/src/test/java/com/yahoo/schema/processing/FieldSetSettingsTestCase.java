// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.derived.TestableDeployLogger;
import com.yahoo.schema.document.MatchType;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.yahoo.schema.ApplicationBuilder.createFromStrings;
import static com.yahoo.schema.document.MatchType.EXACT;
import static com.yahoo.schema.document.MatchType.WORD;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
                        "This may lead to recall and ranking issues. The matching setting that will be used for this fieldset is TEXT. " +
                        "See https://docs.vespa.ai/en/reference/schema-reference.html#fieldset",
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

    @Test
    @Disabled
    // Test that match setting for a field will be a match settings one of the fields
    // in the set has, not the default match setting for a field
    // TODO: This now fails because setting match setting for a fieldset is done after
    // checking if there are inconsistencies in match settings for fields in a fieldset,
    // but code today return if it finds such an inconsistency WITHOUT setting match
    // setting for the fieldset, which means it will end up being the default match setting
    // (TEXT). As shown in this test, it should be either WORD or EXACT (fields are
    // processed in lexical order of fioeld name, so the first field will determine which match
    // setting is used.
    public void inconsistentMatchingShouldStillSetMatchingForFieldSet() throws ParseException {
        var logger = new TestableDeployLogger();

        // a is field with word mathcing => word matching for fieldset
        var builder = createFromStrings(logger, schemaWithMatchSettings("fieldset default { fields: a, b }", "a", "b"));
        assertMatchType(builder, WORD);

        // a is field with exact mathcing => exact matchong for fieldset
        builder = createFromStrings(logger, schemaWithMatchSettings("fieldset default { fields: a, b }", "b", "a"));
        assertMatchType(builder, EXACT);
    }

    private static void assertMatchType(ApplicationBuilder builder, MatchType matchType) {
        var fieldSet = builder.getSchema().fieldSets().userFieldSets().values().iterator().next();
        assertEquals(matchType, fieldSet.getMatching().getType());
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

    private static String schemaWithMatchSettings(String fieldSet, String fieldNameWithWordMatching, String fieldNameWithExactMatching) {
        return """
                  schema index_variants {
                    document index_variants {
                      field %s type string {
                        indexing: index
                        match: word
                      }
                      field %s type string {
                        indexing: index
                        match: exact
                      }
                    }
                  %s
                  }
                """.formatted(fieldNameWithWordMatching, fieldNameWithExactMatching, fieldSet);
    }

}
