// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
import com.yahoo.searchdefinition.UnprocessingSearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Mathias Mølster Lidal
 */
public class BoldingTestCase extends SearchDefinitionTestCase {

    @Test
    public void testBoldingNonString() throws IOException, ParseException {
        try {
            Search search = UnprocessingSearchBuilder.buildUnprocessedFromFile("src/test/processing/boldnonstring.sd");
            new Bolding(search, new BaseDeployLogger(), new RankProfileRegistry(), new QueryProfiles()).process(true);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("'bolding: on' for non-text field"));
        }
    }

}


