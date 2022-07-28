// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.model.application.provider.BaseDeployLogger;

import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class SummaryFieldsMustHaveValidSourceTestCase extends AbstractSchemaTestCase {

    @Test
    void requireThatInvalidSourceIsCaught() throws IOException, ParseException {
        try {
            ApplicationBuilder.buildFromFile("src/test/examples/invalidsummarysource.sd");
            fail("This should throw and never get here");
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'invalidsummarysource', summary class 'baz', summary field 'cox': there is no valid source 'nonexistingfield'.", e.getMessage());
        }
    }

    @Test
    void requireThatInvalidImplicitSourceIsCaught() throws IOException, ParseException {
        try {
            ApplicationBuilder.buildFromFile("src/test/examples/invalidimplicitsummarysource.sd");
            fail("This should throw and never get here");
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'invalidsummarysource', summary class 'baz', summary field 'cox': there is no valid source 'cox'.", e.getMessage());
        }
    }

    @Test
    void requireThatInvalidSelfReferingSingleSource() throws IOException, ParseException {
        try {
            ApplicationBuilder.buildFromFile("src/test/examples/invalidselfreferringsummary.sd");
            fail("This should throw and never get here");
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'invalidselfreferringsummary', summary class 'withid', summary field 'w': there is no valid source 'w'.", e.getMessage());
        }
    }

    @Test
    void requireThatDocumentIdIsAllowedToPass() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/documentidinsummary.sd");
        BaseDeployLogger deployLogger = new BaseDeployLogger();
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        new SummaryFieldsMustHaveValidSource(schema, deployLogger, rankProfileRegistry, new QueryProfiles()).process(true, false);
        assertEquals("documentid", schema.getSummary("withid").getSummaryField("w").getSingleSource());
    }

}
