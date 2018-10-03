// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.model.application.provider.BaseDeployLogger;

import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SummaryFieldsMustHaveValidSourceTestCase extends SearchDefinitionTestCase {

    @Test
    public void requireThatInvalidSourceIsCaught() throws IOException, ParseException {
        try {
            SearchBuilder.buildFromFile("src/test/examples/invalidsummarysource.sd");
            fail("This should throw and never get here");
        } catch (IllegalArgumentException e) {
            assertEquals("For search 'invalidsummarysource', summary class 'baz', summary field 'cox': there is no valid source 'nonexistingfield'.", e.getMessage());
        }
    }

    @Test
    public void requireThatInvalidImplicitSourceIsCaught() throws IOException, ParseException {
        try {
            SearchBuilder.buildFromFile("src/test/examples/invalidimplicitsummarysource.sd");
            fail("This should throw and never get here");
        } catch (IllegalArgumentException e) {
            assertEquals("For search 'invalidsummarysource', summary class 'baz', summary field 'cox': there is no valid source 'cox'.", e.getMessage());
        }
    }

    @Test
    public void requireThatInvalidSelfReferingSingleSource() throws IOException, ParseException {
        try {
            SearchBuilder.buildFromFile("src/test/examples/invalidselfreferringsummary.sd");
            fail("This should throw and never get here");
        } catch (IllegalArgumentException e) {
            assertEquals("For search 'invalidselfreferringsummary', summary class 'withid', summary field 'w': there is no valid source 'w'.", e.getMessage());
        }
    }

    @Test
    public void requireThatDocumentIdIsAllowedToPass() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/documentidinsummary.sd");
        BaseDeployLogger deployLogger = new BaseDeployLogger();
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        new SummaryFieldsMustHaveValidSource(search, deployLogger, rankProfileRegistry, new QueryProfiles()).process(true, false);
        assertEquals("documentid", search.getSummary("withid").getSummaryField("w").getSingleSource());
    }

}
