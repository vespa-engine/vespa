// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.SchemaTestCase;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;
import java.util.Iterator;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests summary extraction
 *
 * @author bratseth
 */
public class SummaryTestCase extends SchemaTestCase {

    @Test
    public void testDeriving() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/simple.sd");
        SummaryClass summary = new SummaryClass(search, search.getSummary("default"), new BaseDeployLogger());
        assertEquals("default", summary.getName());

        Iterator<SummaryClassField> fields = summary.fieldIterator();

        SummaryClassField field;

        assertEquals(13, summary.getFieldCount());

        field = fields.next();
        assertEquals("exactemento", field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING, field.getType());

        field = fields.next();
        assertEquals("exact", field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING, field.getType());

        field = fields.next();
        assertEquals("title", field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING, field.getType());

        field = fields.next();
        assertEquals("description", field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING, field.getType());

        field = fields.next();
        assertEquals("dyndesc", field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING, field.getType());

        field = fields.next();
        assertEquals("longdesc", field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING, field.getType());

        field = fields.next();
        assertEquals("longstat", field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING, field.getType());

        field = fields.next();
        assertEquals("dynlong", field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING, field.getType());

        field = fields.next();
        assertEquals("dyndesc2", field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING, field.getType());

        field = fields.next();
        assertEquals("measurement", field.getName());
        assertEquals(SummaryClassField.Type.INTEGER, field.getType());

        field = fields.next();
        assertEquals("rankfeatures", field.getName());
        assertEquals(SummaryClassField.Type.FEATUREDATA, field.getType());

        field = fields.next();
        assertEquals("summaryfeatures", field.getName());
        assertEquals(SummaryClassField.Type.FEATUREDATA, field.getType());

        field = fields.next();
        assertEquals("documentid", field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING, field.getType());
    }

    @Test
    public void reference_fields_can_be_part_of_summary_classes() throws ParseException {
        Search adSearch = buildCampaignAdModel();

        SummaryClass defaultClass = new SummaryClass(adSearch, adSearch.getSummary("default"), new BaseDeployLogger());
        assertEquals(SummaryClassField.Type.LONGSTRING, defaultClass.getField("campaign_ref").getType());
        assertEquals(SummaryClassField.Type.LONGSTRING, defaultClass.getField("other_campaign_ref").getType());

        SummaryClass myClass = new SummaryClass(adSearch, adSearch.getSummary("my_summary"), new BaseDeployLogger());
        assertNull(myClass.getField("campaign_ref"));
        assertEquals(SummaryClassField.Type.LONGSTRING, myClass.getField("other_campaign_ref").getType());
    }

    private static Search buildCampaignAdModel() throws ParseException {
        SearchBuilder builder = new SearchBuilder();
        builder.importString("search campaign { document campaign {} }");
        builder.importString(joinLines("search ad {",
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
        builder.build();
        return builder.getSearch("ad");
    }

}
