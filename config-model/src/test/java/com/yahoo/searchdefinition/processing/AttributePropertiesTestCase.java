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

import static org.junit.Assert.fail;
/**
 *  Test AttributeProperties processor.
 *
 * @author hmusum
 */
public class AttributePropertiesTestCase extends SearchDefinitionTestCase {

    @Test
    public void testInvalidAttributeProperties() throws IOException, ParseException {
        try {
            Search search = UnprocessingSearchBuilder.buildUnprocessedFromFile("src/test/examples/attributeproperties1.sd");
            new AttributeProperties(search, new BaseDeployLogger(), new RankProfileRegistry(), new QueryProfiles()).process(true);
            fail("attribute property should not be set");
        } catch (RuntimeException e) {
            // empty
        }
    }

    @Test
    public void testValidAttributeProperties() throws IOException, ParseException {
        Search search = UnprocessingSearchBuilder.buildUnprocessedFromFile("src/test/examples/attributeproperties2.sd");
        new AttributeProperties(search, new BaseDeployLogger(), new RankProfileRegistry(), new QueryProfiles()).process(true);
    }

}
