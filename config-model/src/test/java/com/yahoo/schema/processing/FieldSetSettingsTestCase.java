// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.derived.TestableDeployLogger;
import com.yahoo.schema.document.MatchType;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.text.Text;

import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.yahoo.schema.ApplicationBuilder.createFromStrings;
import static com.yahoo.schema.document.MatchType.EXACT;
import static com.yahoo.schema.document.MatchType.WORD;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
                "For schema 'child': " +
                        "The matching settings in fieldset 'default' are inconsistent (explicitly or because of field type). " +
                        "This may lead to recall and ranking issues. The fieldset will use matching TEXT. " +
                        "[ Field 'ci' has matching TEXT,  Field 'ps' has matching WORD] " +
                        "See https://docs.vespa.ai/en/reference/schemas/schemas.html#fieldset",
                "For schema 'child', field 'ps': " +
                        "The normalization settings for the fields in fieldset 'default' are inconsistent (explicitly or because of field type). " +
                        "This may lead to recall and ranking issues. See https://docs.vespa.ai/en/reference/schemas/schemas.html#fieldset"},
                logger.warnings.toArray());
    }

    @Test
    public void illegalFieldTypeMix() {
        var e = assertThrows(IllegalArgumentException.class, () -> createFromStrings(new BaseDeployLogger(), childSd( "fieldset default { fields: ci, pt }"), parentSd()));
        assertEquals("For schema 'child', fieldset 'default': Illegal mixing of tensor fields ['pt'] and non-tensor fields ['ci']", e.getMessage());
    }
    
    @Test
    public void illegalLinguisticsTokensMix() {
        var schema = """
                schema test {
                  document test {
                    field s1 type string {
                      indexing: index
                      linguistics {
                        tokens: alternatives
                      }
                    }
                    field s2 type string {
                      indexing: index
                      linguistics {
                        index {
                          tokens: original
                        }
                        search {
                          tokens: alternatives   # Legal combination with s1 since only the query side need to be consistent
                        }
                      }
                    }
                    field s3 type string {
                      indexing: index
                      linguistics {
                        tokens: original-and-alternatives   # Not legal combination with s1
                      }
                    }
                  }
                  fieldset t1t2 {
                    fields: s1, s2
                  }
                  fieldset t1t3 {
                    fields: s1, s3
                  }
                }
                """;
        var e = assertThrows(IllegalArgumentException.class, () -> createFromStrings(new BaseDeployLogger(), schema));
        assertEquals("For schema 'test', fieldset 't1t3': Illegal mixing of linguistics search tokens/stemming settings" +
                     ": field 's1' resolves to 'stemming all-stems', while field 's3' resolves to 'stemming multiple'",
                     e.getMessage());
    }

    @Test
    public void illegalLinguisticsTokensMixIsIndependentOfFieldOrder() {
        // s2 and s3 are inconsistent, and s3 sets tokens, so this is an error. Whether s1 is
        // declared first must not change that: comparing every field with the first one only
        // made an error disappear as soon as an agreeing field was put in front of the pair.
        var conflicting = """
                    field s2 type string {
                      indexing: index
                      stemming: shortest
                    }
                    field s3 type string {
                      indexing: index
                      linguistics {
                        tokens: original
                      }
                    }
                """;
        var leading = """
                    field s1 type string {
                      indexing: index
                      stemming: none
                    }
                """;
        var expected = "For schema 'test', fieldset 'fs': Illegal mixing of linguistics search tokens/stemming " +
                       "settings: field 's2' resolves to 'stemming shortest', while field 's3' resolves to 'stemming none'";
        for (var fields : List.of(conflicting, leading + conflicting)) {
            var schema = "schema test {\n  document test {\n" + fields + "  }\n  fieldset fs { fields: " +
                         (fields.contains("s1") ? "s1, " : "") + "s2, s3 }\n}\n";
            var e = assertThrows(IllegalArgumentException.class,
                                 () -> createFromStrings(new BaseDeployLogger(), schema));
            assertEquals(expected, e.getMessage(), schema);
        }
    }

    @Test
    public void tokensMixedWithAWordMatchedFieldIsLegal() {
        // A word matched field produces a single token and cannot stem, so it cannot be
        // inconsistent with anything: LinguisticsSettings has cleared its tokens setting.
        var logger = new TestableDeployLogger();
        var schema = """
                schema test {
                  document test {
                    field s1 type string {
                      indexing: index
                      match: word
                    }
                    field s2 type string {
                      indexing: index
                      linguistics {
                        tokens: alternatives
                      }
                    }
                  }
                  fieldset t1t2 {
                    fields: s1, s2
                  }
                }
                """;
        assertDoesNotThrow(() -> createFromStrings(logger, schema));
        // The matching and normalization inconsistencies of a word/text mix are warned about
        // elsewhere and are not the subject here: no stemming inconsistency must be reported.
        assertArrayEquals(new String[]{},
                          logger.warnings.stream().filter(warning -> warning.contains("stemming")).toArray());
    }

    @Test
    public void stemmingConsistencyIsCheckedOnTheResolvedSetting() {
        // Pins the behaviour of checkStemming for plain stemming settings, which changed when
        // the tokens setting was added: it used to compare the raw, nullable setting of each
        // field, where null doubled as the "no field seen yet" sentinel. A field which simply
        // uses the schema default must not be reported as inconsistent with a field which sets
        // that same value explicitly, and an actual mismatch must be reported in either order.
        assertStemmingWarnings("stemming: best", "", new String[]{});
        assertStemmingWarnings("", "stemming: best", new String[]{});

        var expected = new String[]{"For schema 'test', field 's2': The stemming settings for the fields in the " +
                                    "fieldset 't1t2' are inconsistent. This may lead to recall and ranking issues. " +
                                    "See " + FIELDSET_DOC_URL};
        assertStemmingWarnings("stemming: shortest", "", expected);
        assertStemmingWarnings("", "stemming: shortest", expected);
    }

    private void assertStemmingWarnings(String s1Stemming, String s2Stemming, String[] expectedWarnings) {
        var logger = new TestableDeployLogger();
        var schema = """
                schema test {
                  document test {
                    field s1 type string {
                      indexing: index
                      %s
                    }
                    field s2 type string {
                      indexing: index
                      %s
                    }
                  }
                  fieldset t1t2 {
                    fields: s1, s2
                  }
                }
                """.formatted(s1Stemming, s2Stemming);
        assertDoesNotThrow(() -> createFromStrings(logger, schema));
        assertArrayEquals(expectedWarnings, logger.warnings.toArray(), schema);
    }

    private static final String FIELDSET_DOC_URL = "https://docs.vespa.ai/en/reference/schemas/schemas.html#fieldset";

    @Test
    public void tokensMixedWithTheDefaultStemmingIsLegal() {
        // s2 has no stemming setting and so uses the schema default, best, which is what
        // 'tokens: first-alternative' resolves to
        var logger = new TestableDeployLogger();
        var schema = """
                schema test {
                  document test {
                    field s1 type string {
                      indexing: index
                      linguistics {
                        tokens: first-alternative
                      }
                    }
                    field s2 type string {
                      indexing: index
                    }
                  }
                  fieldset t1t2 {
                    fields: s1, s2
                  }
                }
                """;
        assertDoesNotThrow(() -> createFromStrings(logger, schema));
        assertArrayEquals(new String[]{}, logger.warnings.toArray());
    }

    @Test
    public void tokensMixedWithADifferentDefaultStemmingIsIllegal() {
        var schema = """
                schema test {
                  document test {
                    field s1 type string {
                      indexing: index
                      linguistics {
                        tokens: alternatives
                      }
                    }
                    field s2 type string {
                      indexing: index
                    }
                  }
                  fieldset t1t2 {
                    fields: s1, s2
                  }
                }
                """;
        var e = assertThrows(IllegalArgumentException.class, () -> createFromStrings(new BaseDeployLogger(), schema));
        assertEquals("For schema 'test', fieldset 't1t2': Illegal mixing of linguistics search tokens/stemming settings" +
                     ": field 's1' resolves to 'stemming all-stems', while field 's2' resolves to 'stemming best'",
                     e.getMessage());
    }

    @Test
    public void tokensMixedWithANonStringFieldIsLegal() {
        // Stemming, and thereby tokens, does not apply to an int field
        var logger = new TestableDeployLogger();
        var schema = """
                schema test {
                  document test {
                    field i1 type int {
                      indexing: attribute
                    }
                    field s1 type string {
                      indexing: index
                      linguistics {
                        tokens: alternatives
                      }
                    }
                  }
                  fieldset i1s1 {
                    fields: i1, s1
                  }
                }
                """;
        assertDoesNotThrow(() -> createFromStrings(logger, schema));
        assertArrayEquals(new String[]{}, logger.warnings.toArray());
    }

    @Test
    public void equivalentLinguisticsTokensAndStemmingMixIsLegal() {
        // 'tokens: first-alternative' and 'stemming: best' both resolve to the same effective
        // search stemming, so mixing them in a fieldset is not an error.
        var schema = """
                schema test {
                  document test {
                    field s1 type string {
                      indexing: index
                      stemming: best
                    }
                    field s2 type string {
                      indexing: index
                      linguistics {
                        tokens: first-alternative
                      }
                    }
                  }
                  fieldset t1t2 {
                    fields: s1, s2
                  }
                }
                """;
        assertDoesNotThrow(() -> createFromStrings(new BaseDeployLogger(), schema));
    }

    @Test
    public void checkStemmingUsesEffectiveTokensAwareStemming() {
        // Same schema as above: 'tokens: first-alternative' and 'stemming: best' resolve to the same
        // effective stemming, so checkStemming (which only warns) must not flag this as inconsistent either.
        var logger = new TestableDeployLogger();
        var schema = """
                schema test {
                  document test {
                    field s1 type string {
                      indexing: index
                      stemming: best
                    }
                    field s2 type string {
                      indexing: index
                      linguistics {
                        tokens: first-alternative
                      }
                    }
                  }
                  fieldset t1t2 {
                    fields: s1, s2
                  }
                }
                """;
        assertDoesNotThrow(() -> createFromStrings(logger, schema));
        assertArrayEquals(new String[]{}, logger.warnings.toArray());
    }

    @Test
    public void tokensMixedWithIndexLevelStemmingIsCheckedOnTheEffectiveSetting() {
        // The stemming of an index block, in the field or at the schema level, is what the query
        // side actually uses (see IndexInfo), so the fieldset check must resolve it the same way:
        // neither reject a fieldset index-info would have made consistent, nor accept one it would not.
        var fieldLevel = "index { stemming: none }";
        var schemaLevel = "index s2 { stemming: none }";
        for (var indexBlocks : List.of(new String[]{fieldLevel, ""}, new String[]{"", schemaLevel})) {
            var logger = new TestableDeployLogger();
            var agreeing = tokensAndIndexStemmingSchema("original", indexBlocks[0], indexBlocks[1]);
            assertDoesNotThrow(() -> createFromStrings(logger, agreeing), agreeing);
            assertArrayEquals(new String[]{}, logger.warnings.toArray(), agreeing);

            var conflicting = tokensAndIndexStemmingSchema("first-alternative", indexBlocks[0], indexBlocks[1]);
            var e = assertThrows(IllegalArgumentException.class,
                                 () -> createFromStrings(new BaseDeployLogger(), conflicting), conflicting);
            assertEquals("For schema 'test', fieldset 't1t2': Illegal mixing of linguistics search tokens/stemming " +
                         "settings: field 's1' resolves to 'stemming best', while field 's2' resolves to 'stemming none'",
                         e.getMessage(), conflicting);
        }
    }

    /** Returns a schema where s1 sets the given tokens, and s2 has the given index block in it and at the schema level */
    private static String tokensAndIndexStemmingSchema(String tokens, String fieldIndexBlock, String schemaIndexBlock) {
        return """
                schema test {
                  document test {
                    field s1 type string {
                      indexing: index
                      linguistics {
                        tokens: %s
                      }
                    }
                    field s2 type string {
                      indexing: index
                      %s
                    }
                  }
                  %s
                  fieldset t1t2 {
                    fields: s1, s2
                  }
                }
                """.formatted(tokens, fieldIndexBlock, schemaIndexBlock);
    }


    @Test
    public void unstemmedTextInAStemmedFieldsetIsWarnedAbout() {
        // A uri field is text matched, so checkMatching sees nothing wrong with mixing it into a
        // text fieldset, but UriHack turns its stemming off: the query side would stem terms for
        // the fieldset which this field's content was never stemmed with. Nothing warned about this.
        assertArrayEquals(new String[]{
                "For schema 'test', field 'u1': The fields in fieldset 't1t2' are stemmed when searched, " +
                "but the content of this field is not stemmed, so it will not match a stemmed term. " +
                "This may lead to recall and ranking issues. See " + FIELDSET_DOC_URL},
                unstemmedTextWarnings("""
                    field s1 type string {
                      indexing: index
                    }
                    field u1 type uri {
                      indexing: index
                    }
                """, "s1, u1"));
    }

    @Test
    public void unstemmedTextInAFieldsetWhichIsNotStemmedIsLegal() {
        // Nothing is stemmed here, so there is no mismatch to warn about
        assertArrayEquals(new String[]{}, unstemmedTextWarnings("""
                    field s1 type string {
                      indexing: index
                      stemming: none
                    }
                    field u1 type uri {
                      indexing: index
                    }
                """, "s1, u1"));
    }

    @Test
    public void unstemmedTextFromATokensSettingIsWarnedAbout() {
        // The fieldset is stemmed because of a tokens setting rather than a stemming one
        assertArrayEquals(new String[]{
                "For schema 'test', field 'u1': The fields in fieldset 't1t2' are stemmed when searched, " +
                "but the content of this field is not stemmed, so it will not match a stemmed term. " +
                "This may lead to recall and ranking issues. See " + FIELDSET_DOC_URL},
                unstemmedTextWarnings("""
                    field s1 type string {
                      indexing: index
                      linguistics { tokens: alternatives }
                    }
                    field u1 type uri {
                      indexing: index
                    }
                """, "s1, u1"));
    }

    @Test
    public void aWordMatchedFieldIsLeftToTheMatchingCheck() {
        // A word matched field makes the whole fieldset word matched, so IndexInfo emits no stem
        // command for it and there is nothing to warn about here. checkMatching reports the mix.
        var warnings = unstemmedTextWarnings("""
                    field s1 type string {
                      indexing: index
                    }
                    field w1 type string {
                      indexing: index
                      match: word
                    }
                """, "s1, w1");
        assertArrayEquals(new String[]{}, java.util.Arrays.stream(warnings)
                                                         .filter(warning -> warning.contains("stemmed"))
                                                         .toArray());
    }

    @Test
    public void aFieldWhichHoldsNoTextIsNotWarnedAbout() {
        // Stemming a query term does not change whether it matches the content of an int field
        assertArrayEquals(new String[]{}, unstemmedTextWarnings("""
                    field s1 type string {
                      indexing: index
                    }
                    field i1 type int {
                      indexing: attribute
                    }
                """, "s1, i1"));
    }

    /** Returns the warnings from building a schema with the given document fields and fieldset fields */
    private String[] unstemmedTextWarnings(String documentFields, String fieldSetFields) {
        var logger = new TestableDeployLogger();
        var schema = """
                schema test {
                  document test {
                %s
                  }
                  fieldset t1t2 {
                    fields: %s
                  }
                }
                """.formatted(documentFields, fieldSetFields);
        assertDoesNotThrow(() -> createFromStrings(logger, schema), schema);
        return logger.warnings.toArray(new String[0]);
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

        // a is field with word matching => word matching for fieldset
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
        return Text.format("""
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
                """, fieldNameWithWordMatching, fieldNameWithExactMatching, fieldSet);
    }

}
