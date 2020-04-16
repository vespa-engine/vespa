// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.derived.DerivedConfiguration;
import com.yahoo.searchdefinition.parser.ParseException;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import com.yahoo.yolean.Exceptions;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class IncorrectRankingExpressionFileRefTestCase extends SchemaTestCase {

    @Test
    public void testIncorrectRef() throws IOException, ParseException {
        try {
            RankProfileRegistry registry = new RankProfileRegistry();
            Search search = SearchBuilder.buildFromFile("src/test/examples/incorrectrankingexpressionfileref.sd",
                                                        registry,
                                                        new QueryProfileRegistry());
            new DerivedConfiguration(search, new BaseDeployLogger(), new TestProperties(), registry, new QueryProfileRegistry(), new ImportedMlModels()); // cause rank profile parsing
            fail("parsing should have failed");
        } catch (IllegalArgumentException e) {
            String message = Exceptions.toMessageString(e);
            assertTrue(message.contains("Could not read ranking expression file"));
            assertTrue(message.contains("wrongending.expr.expression"));
        }
    }

}
