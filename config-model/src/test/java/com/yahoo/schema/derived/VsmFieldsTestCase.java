// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Case;
import com.yahoo.schema.document.MatchType;
import com.yahoo.schema.document.Matching;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.TemporarySDField;
import com.yahoo.vespa.config.search.vsm.VsmfieldsConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author geirst
 */
public class VsmFieldsTestCase {

    private static Schema createSchema() {
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
        field.parseIndexingScript("{ summary }");
        schema.getDocument().addField(field);
        VsmfieldsConfig cfg = vsmfieldsConfig(schema);

        assertEquals(1, cfg.fieldspec().size());
        VsmfieldsConfig.Fieldspec fieldSpec = cfg.fieldspec().get(0);
        assertEquals("ref_field", fieldSpec.name());
        assertEquals(VsmfieldsConfig.Fieldspec.Searchmethod.NONE, fieldSpec.searchmethod());
    }

    @Test
    void test_exact_string() {
        Schema schema = createSchema();
        SDField field = new TemporarySDField(schema.getDocument(), "f", DataType.STRING);
        field.parseIndexingScript("{ index }");
        field.setMatching(new Matching(MatchType.EXACT).setCase(Case.CASED));
        schema.getDocument().addField(field);
        VsmfieldsConfig cfg = vsmfieldsConfig(schema);
        VsmfieldsConfig.Fieldspec fieldSpec = cfg.fieldspec().get(0);
        assertEquals("f", fieldSpec.name());
        assertEquals(VsmfieldsConfig.Fieldspec.Searchmethod.AUTOUTF8, fieldSpec.searchmethod());
        assertEquals(VsmfieldsConfig.Fieldspec.Normalize.LOWERCASE, fieldSpec.normalize());
        assertEquals("exact", fieldSpec.arg1());
    }

    @Test
    void test_string() {
        Schema schema = createSchema();
        SDField field = new TemporarySDField(schema.getDocument(), "f", DataType.STRING);
        field.parseIndexingScript("{ index }");
        field.setMatching(new Matching(MatchType.TEXT));
        schema.getDocument().addField(field);
        VsmfieldsConfig cfg = vsmfieldsConfig(schema);
        VsmfieldsConfig.Fieldspec fieldSpec = cfg.fieldspec().get(0);
        assertEquals("f", fieldSpec.name());
        assertEquals(VsmfieldsConfig.Fieldspec.Searchmethod.AUTOUTF8, fieldSpec.searchmethod());
        assertEquals(VsmfieldsConfig.Fieldspec.Normalize.LOWERCASE_AND_FOLD, fieldSpec.normalize());
        assertEquals("", fieldSpec.arg1());
    }

    @Test
    void test_cased_string() {
        Schema schema = createSchema();
        SDField field = new TemporarySDField(schema.getDocument(), "f", DataType.STRING);
        field.parseIndexingScript("{ index }");
        field.setMatching(new Matching(MatchType.TEXT).setCase(Case.CASED));
        schema.getDocument().addField(field);
        VsmfieldsConfig cfg = vsmfieldsConfig(schema);
        VsmfieldsConfig.Fieldspec fieldSpec = cfg.fieldspec().get(0);
        assertEquals("f", fieldSpec.name());
        assertEquals(VsmfieldsConfig.Fieldspec.Searchmethod.AUTOUTF8, fieldSpec.searchmethod());
        assertEquals(VsmfieldsConfig.Fieldspec.Normalize.NONE, fieldSpec.normalize());
        assertEquals("", fieldSpec.arg1());
    }
}
