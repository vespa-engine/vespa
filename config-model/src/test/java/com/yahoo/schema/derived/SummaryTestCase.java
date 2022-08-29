// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.config.search.SummaryConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Iterator;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests summary extraction
 *
 * @author bratseth
 */
public class SummaryTestCase extends AbstractSchemaTestCase {

    @Test
    void deriveRawAsBase64() throws ParseException {
        String sd = joinLines(
                "schema s {",
                "  document s {",
                "      field raw_field type raw {",
                "          indexing: summary",
                "      }",
                "  }",
                "}");
        Schema schema = ApplicationBuilder.createFromString(sd).getSchema();
        SummaryClass summary = new SummaryClass(schema, schema.getSummary("default"), new BaseDeployLogger());
        assertEquals(SummaryClassField.Type.RAW, summary.fields().get("raw_field").getType());
    }

    @Test
    void deriveRawAsLegacy() throws ParseException {
        String sd = joinLines(
                "schema s {",
                "  raw-as-base64-in-summary: false",
                "  document s {",
                "      field raw_field type raw {",
                "          indexing: summary",
                "      }",
                "  }",
                "}");
        Schema schema = ApplicationBuilder.createFromString(sd).getSchema();
        SummaryClass summary = new SummaryClass(schema, schema.getSummary("default"), new BaseDeployLogger());
        assertEquals(SummaryClassField.Type.DATA, summary.fields().get("raw_field").getType());
    }

    @Test
    void testDeriving() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/simple.sd");
        SummaryClass summary = new SummaryClass(schema, schema.getSummary("default"), new BaseDeployLogger());
        assertEquals("default", summary.getName());

        Iterator<SummaryClassField> fields = summary.fields().values().iterator();

        SummaryClassField field;

        assertEquals(13, summary.fields().size());

        assertSummaryField("exactemento", SummaryClassField.Type.LONGSTRING, fields.next());
        assertSummaryField("exact", SummaryClassField.Type.LONGSTRING, fields.next());
        assertSummaryField("title", SummaryClassField.Type.LONGSTRING, fields.next());
        assertSummaryField("description", SummaryClassField.Type.LONGSTRING, fields.next());
        assertSummaryField("dyndesc", SummaryClassField.Type.LONGSTRING, "dynamicteaser", "dyndesc", fields.next());
        assertSummaryField("longdesc", SummaryClassField.Type.LONGSTRING, fields.next());
        assertSummaryField("longstat", SummaryClassField.Type.LONGSTRING, fields.next());
        assertSummaryField("dynlong", SummaryClassField.Type.LONGSTRING, "dynamicteaser", "dynlong", fields.next());
        assertSummaryField("dyndesc2", SummaryClassField.Type.LONGSTRING, "dynamicteaser", "dyndesc2", fields.next());
        assertSummaryField("measurement", SummaryClassField.Type.INTEGER, "attribute", "measurement", fields.next());
        assertSummaryField("rankfeatures", SummaryClassField.Type.FEATUREDATA, "rankfeatures", fields.next());
        assertSummaryField("summaryfeatures", SummaryClassField.Type.FEATUREDATA, "summaryfeatures", fields.next());
        assertSummaryField("documentid", SummaryClassField.Type.LONGSTRING, "documentid", fields.next());
    }

    @Test
    void reference_fields_can_be_part_of_summary_classes() throws ParseException {
        Schema adSchema = buildCampaignAdModel();

        SummaryClass defaultClass = new SummaryClass(adSchema, adSchema.getSummary("default"), new BaseDeployLogger());
        assertEquals(SummaryClassField.Type.LONGSTRING, defaultClass.fields().get("campaign_ref").getType());
        assertEquals(SummaryClassField.Type.LONGSTRING, defaultClass.fields().get("other_campaign_ref").getType());

        SummaryClass myClass = new SummaryClass(adSchema, adSchema.getSummary("my_summary"), new BaseDeployLogger());
        assertNull(myClass.fields().get("campaign_ref"));
        assertEquals(SummaryClassField.Type.LONGSTRING, myClass.fields().get("other_campaign_ref").getType());
    }

    private void assertSummaryField(String expName, SummaryClassField.Type expType, SummaryClassField field) {
        assertSummaryField(expName, expType, "", "", field);
    }

    private void assertSummaryField(String expName, SummaryClassField.Type expType, String expCommand, SummaryClassField field) {
        assertSummaryField(expName, expType, expCommand, "", field);
    }

    private void assertSummaryField(String expName, SummaryClassField.Type expType,
                                    String expCommand, String expSource, SummaryClassField field) {
        assertEquals(expName, field.getName());
        assertEquals(expType, field.getType());
        assertEquals(expCommand, field.getCommand());
        assertEquals(expSource, field.getSource());

    }

    private static Schema buildCampaignAdModel() throws ParseException {
        ApplicationBuilder builder = new ApplicationBuilder();
        builder.addSchema("search campaign { document campaign {} }");
        builder.addSchema(joinLines("search ad {",
                                    "  document ad {",
                                    "    field campaign_ref type reference<campaign> {",
                                    "      indexing: summary | attribute",
                                    "    }",
                                    "    field other_campaign_ref type reference<campaign> {",
                                    "      indexing: summary | attribute",
                                    "    }",
                                    "  }",
                                    "  document-summary my_summary {",
                                    "    summary other_campaign_ref type reference<campaign> {}",
                                    "  }",
                                    "}"));
        builder.build(true);
        return builder.getSchema("ad");
    }

    @Test
    void omit_summary_features_specified_for_document_summary() throws ParseException {
        String sd = joinLines(
                "schema test {",
                "  document test {",
                "    field foo type string { indexing: summary }",
                "  }",
                "  document-summary bar {",
                "    summary foo type string {}",
                "    omit-summary-features",
                "  }",
                "  document-summary baz {",
                "    summary foo type string {}",
                "  }",
                "}");
        var search = ApplicationBuilder.createFromString(sd).getSchema();
        assertOmitSummaryFeatures(true, search, "bar");
        assertOmitSummaryFeatures(false, search, "baz");
    }

    private void assertOmitSummaryFeatures(boolean expected, Schema schema, String summaryName) {
        var summary = new SummaryClass(schema, schema.getSummary(summaryName), new BaseDeployLogger());
        var config = new SummaryConfig.Classes(summary.getSummaryClassConfig());
        assertEquals(expected, config.omitsummaryfeatures());
    }

}
