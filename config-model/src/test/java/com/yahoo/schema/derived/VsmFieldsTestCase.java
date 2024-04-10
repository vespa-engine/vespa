// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Case;
import com.yahoo.schema.document.MatchType;
import com.yahoo.schema.document.Matching;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.TemporarySDField;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.config.search.vsm.VsmfieldsConfig;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author geirst
 */
public class VsmFieldsTestCase {

     static Schema createSchema() {
        Schema schema = new Schema("test", MockApplicationPackage.createEmpty(), new MockFileRegistry(), new TestableDeployLogger(), new TestProperties());
        var sdoc = new SDDocumentType("test");
        schema.addDocument(sdoc);
        return schema;
     }
     private static VsmfieldsConfig vsmfieldsConfig(Schema schema) {
         VsmFields vsmFields = new VsmFields(schema);
         VsmfieldsConfig.Builder cfgBuilder = new VsmfieldsConfig.Builder();
         vsmFields.getConfig(cfgBuilder);
         return cfgBuilder.build();
     }

    @Test
     void reference_type_field_is_unsearchable() {
         Schema schema = createSchema();
         SDField field = new TemporarySDField(schema.getDocument(), "ref_field", NewDocumentReferenceDataType.forDocumentName("parent_type"));
         field.parseIndexingScript(schema.getName(), "{ summary }");
         schema.getDocument().addField(field);
         VsmfieldsConfig cfg = vsmfieldsConfig(schema);

         assertEquals(1, cfg.fieldspec().size());
         VsmfieldsConfig.Fieldspec fieldSpec = cfg.fieldspec().get(0);
         assertEquals("ref_field", fieldSpec.name());
         assertEquals(VsmfieldsConfig.Fieldspec.Searchmethod.NONE, fieldSpec.searchmethod());
     }

     private void testIndexMatching(Matching matching, VsmfieldsConfig.Fieldspec.Normalize.Enum normalize, String arg1) {
         Schema schema = createSchema();
         SDField field = new TemporarySDField(schema.getDocument(), "f", DataType.STRING);
         field.parseIndexingScript(schema.getName(), "{ index }");
         field.setMatching(matching);
         schema.getDocument().addField(field);
         VsmfieldsConfig cfg = vsmfieldsConfig(schema);
         VsmfieldsConfig.Fieldspec fieldSpec = cfg.fieldspec().get(0);
         assertEquals("f", fieldSpec.name());
         assertEquals(VsmfieldsConfig.Fieldspec.Searchmethod.AUTOUTF8, fieldSpec.searchmethod());
         assertEquals(normalize, fieldSpec.normalize());
         assertEquals(arg1, fieldSpec.arg1());
     }

     @Test
     void test_exact_string() {
         testIndexMatching(new Matching(MatchType.TEXT),
                 VsmfieldsConfig.Fieldspec.Normalize.LOWERCASE_AND_FOLD, "");
         testIndexMatching(new Matching(MatchType.TEXT).setCase(Case.CASED),
                 VsmfieldsConfig.Fieldspec.Normalize.NONE, "");
         testIndexMatching(new Matching(MatchType.EXACT).setCase(Case.CASED),
                 VsmfieldsConfig.Fieldspec.Normalize.LOWERCASE, "exact");
         testIndexMatching(new Matching(MatchType.WORD),
                 VsmfieldsConfig.Fieldspec.Normalize.LOWERCASE, "word");
         testIndexMatching(new Matching(MatchType.WORD).setCase(Case.CASED),
                 VsmfieldsConfig.Fieldspec.Normalize.NONE, "word");
     }

    private static Set<String> getIndexes(VsmfieldsConfig config, String field) {
        var indexes = new HashSet<String>();
        var doctype = config.documenttype(0);
        for (var index : doctype.index()) {
            for (var indexField : index.field()) {
                if (field.equals(indexField.name())) {
                    indexes.add(index.name());
                    break;
                }
            }
        }
        return indexes;
    }

    @Test
    void deriveIndexFromNestedAttributes() throws ParseException {
        String sd = joinLines(
                "schema test {",
                "  document test {",
                "      field map_field type map<string,int> {",
                "          indexing: summary",
                "          struct-field key { indexing: attribute }",
                "          struct-field value { indexing: attribute }",
                "      }",
                "  }",
                "}");
        var schema = ApplicationBuilder.createFromString(sd).getSchema();
        var config = vsmfieldsConfig(schema);
        assertEquals(Set.of("map_field", "map_field.key"), getIndexes(config, "map_field.key"));
        assertEquals(Set.of("map_field", "map_field.value"), getIndexes(config, "map_field.value"));
    }

    @Test
    void deriveIndexFromIndexStatement() throws ParseException {
        String sd = joinLines(
                "schema test {",
                "  document test {",
                "      field map_field type map<string,int> {",
                "          indexing: summary | index",
                "      }",
                "  }",
                "}");
        var schema = ApplicationBuilder.createFromString(sd).getSchema();
        var config = vsmfieldsConfig(schema);
        assertEquals(Set.of("map_field", "map_field.key"), getIndexes(config, "map_field.key"));
        assertEquals(Set.of("map_field", "map_field.value"), getIndexes(config, "map_field.value"));
    }

    @Test
    void positionFieldTypeBlocksderivingOfIndexFromNestedAttributes() throws ParseException {
        String sd = joinLines(
                "schema test {",
                "  document test {",
                "      field pos type position {",
                "          indexing: attribute | summary",
                "          struct-field x { indexing: attribute }",
                "          struct-field y { indexing: attribute }",
                "      }",
                "  }",
                "}");
        var schema = ApplicationBuilder.createFromString(sd).getSchema();
        var config = vsmfieldsConfig(schema);
        assertEquals(Set.of("pos"), getIndexes(config, "pos"));
        assertEquals(Set.of(), getIndexes(config, "pos.x"));
        assertEquals(Set.of(), getIndexes(config, "pos.y"));
    }

}
