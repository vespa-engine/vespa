// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.RankType;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.Stemming;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.schema.processing.MakeAliases;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests importing of search definitions
 *
 * @author bratseth
 */
public class SchemaImporterTestCase extends AbstractSchemaTestCase {

    @Test
    @SuppressWarnings("deprecation")
    void testSimpleImporting() throws IOException, ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder sb = new ApplicationBuilder(rankProfileRegistry, new QueryProfileRegistry());
        sb.addSchemaFile("src/test/examples/simple.sd");
        sb.build(true);
        Schema schema = sb.getSchema();
        assertEquals("simple", schema.getName());
        assertTrue(schema.hasDocument());

        SDDocumentType document = schema.getDocument();
        assertEquals("simple", document.getName());
        assertEquals(23, document.getFieldCount());

        SDField field;
        Attribute attribute;

        new MakeAliases(schema, new BaseDeployLogger(), rankProfileRegistry, new QueryProfiles()).process(true, false);

        // First field
        field = (SDField) document.getField("title");
        assertEquals(DataType.STRING, field.getDataType());
        assertEquals("{ input title | tokenize normalize stem:\"BEST\" | summary title | index title; }", field.getIndexingScript().toString());
        assertFalse(schema.getIndex("default").isPrefix());
        assertTrue(schema.getIndex("title").isPrefix());
        Iterator<String> titleAliases = schema.getIndex("title").aliasIterator();
        assertEquals("aliaz", titleAliases.next());
        assertEquals("analias.totitle", titleAliases.next());
        assertEquals("analias.todefault",
                schema.getIndex("default").aliasIterator().next());
        assertEquals(RankType.IDENTITY, field.getRankType());
        assertEquals(0, field.getAttributes().size());
        assertNull(field.getStemming());
        assertTrue(field.getNormalizing().doRemoveAccents());

        // Second field
        field = (SDField) document.getField("description");
        assertEquals(RankType.ABOUT, field.getRankType());
        assertEquals(SummaryTransform.NONE,
                field.getSummaryField("description").getTransform());
        assertEquals(SummaryTransform.DYNAMICTEASER,
                field.getSummaryField("dyndesc").getTransform());
        assertNull(field.getStemming());
        assertTrue(field.getNormalizing().doRemoveAccents());
        assertEquals("hallo", schema.getIndex("description").aliasIterator().next());

        // Third field
        field = (SDField) document.getField("chatter");
        assertEquals(RankType.ABOUT, field.getRankType());
        assertNull(field.getStemming());
        assertTrue(field.getNormalizing().doRemoveAccents());

        // Fourth field
        field = (SDField) document.getField("category");
        assertEquals(0, field.getAttributes().size());
        assertEquals(Stemming.NONE, field.getStemming());
        assertFalse(field.getNormalizing().doRemoveAccents());

        // Fifth field
        field = (SDField) document.getField("popularity");
        assertEquals("{ input popularity | attribute popularity; }",
                field.getIndexingScript().toString());

        // Sixth field
        field = (SDField) document.getField("measurement");
        assertEquals(DataType.INT, field.getDataType());
        assertEquals(RankType.EMPTY, field.getRankType());
        assertEquals(1, field.getAttributes().size());

        // Seventh field
        field = schema.getConcreteField("categories");
        assertEquals("{ input categories_src | lowercase | normalize | tokenize normalize stem:\"BEST\" | index categories; }",
                field.getIndexingScript().toString());

        // Eight field
        field = schema.getConcreteField("categoriesagain");
        assertEquals("{ input categoriesagain_src | lowercase | normalize | tokenize normalize stem:\"BEST\" | index categoriesagain; }",
                field.getIndexingScript().toString());

        // Ninth field
        field = schema.getConcreteField("exactemento");
        assertEquals("{ input exactemento_src | lowercase | tokenize normalize stem:\"BEST\" | index exactemento | summary exactemento; }",
                field.getIndexingScript().toString());

        // Tenth field
        field = schema.getConcreteField("category_arr");
        assertEquals(1, field.getAttributes().size());
        attribute = field.getAttributes().get("category_arr");
        assertNotNull(attribute);
        assertEquals("category_arr", attribute.getName());
        assertEquals(Attribute.Type.STRING, attribute.getType());
        assertEquals(Attribute.CollectionType.ARRAY, attribute.getCollectionType());

        // Eleventh field
        field = schema.getConcreteField("measurement_arr");
        assertEquals(1, field.getAttributes().size());
        attribute = field.getAttributes().get("measurement_arr");
        assertEquals("measurement_arr", attribute.getName());
        assertEquals(Attribute.Type.INTEGER, attribute.getType());
        assertEquals(Attribute.CollectionType.ARRAY, attribute.getCollectionType());

        // Rank Profiles
        RankProfile profile = rankProfileRegistry.get(schema, "default");
        assertNotNull(profile);
        assertTrue(profile.inheritedNames().isEmpty());
        assertNull(profile.getDeclaredRankSetting("measurement", RankProfile.RankSetting.Type.RANKTYPE));
        assertEquals(RankType.EMPTY,
                profile.getRankSetting("measurement", RankProfile.RankSetting.Type.RANKTYPE).getValue());
        profile = rankProfileRegistry.get(schema, "experimental");
        assertNotNull(profile);
        assertEquals("default", profile.inheritedNames().get(0));
        assertEquals(RankType.IDENTITY,
                profile.getDeclaredRankSetting("measurement", RankProfile.RankSetting.Type.RANKTYPE).getValue());

        profile = rankProfileRegistry.get(schema, "other");
        assertNotNull(profile);
        assertEquals("experimental", profile.inheritedNames().get(0));

        // The extra-document field
        SDField exact = schema.getConcreteField("exact");
        assertNotNull(exact, "Extra field was parsed");
        assertEquals("exact", exact.getName());
        assertEquals(Stemming.NONE, exact.getStemming());
        assertFalse(exact.getNormalizing().doRemoveAccents());
        assertEquals("{ input title . \" \" . input category | tokenize | summary exact | index exact; }",
                exact.getIndexingScript().toString());
        assertEquals(RankType.IDENTITY, exact.getRankType());
    }

    @Test
    void testDocumentImporting() throws IOException, ParseException {
        try {
            // Having two documents in one sd-file is illegal.
            ApplicationBuilder.buildFromFile("src/test/examples/documents.sd");
            fail();
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    void testIdImporting() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/strange.sd");
        SDField idecidemyide = (SDField) schema.getDocument().getField("idecidemyide");
        assertEquals(5, idecidemyide.getId());
        SDField sodoi = (SDField) schema.getDocument().getField("sodoi");
        assertEquals(7, sodoi.getId());
    }

}
