// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
import com.yahoo.searchdefinition.UnprocessingSearchBuilder;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
/**
 * @author baldersheim
 */
public class IntegerIndex2AttributeTestCase extends SearchDefinitionTestCase {
    @Test
    public void testIntegerIndex2Attribute() throws IOException, ParseException {
        Search search = UnprocessingSearchBuilder.buildUnprocessedFromFile("src/test/examples/integerindex2attribute.sd");
        search.process();
        new IntegerIndex2Attribute(search, new BaseDeployLogger(), new RankProfileRegistry(), new QueryProfiles()).process();

        SDField f;
        f = search.getField("s1");
        assertTrue(f.getAttributes().isEmpty());
        assertTrue(f.existsIndex("s1"));
        f = search.getField("s2");
        assertEquals(f.getAttributes().size(), 1);
        assertTrue(f.existsIndex("s2"));

        f = search.getField("as1");
        assertTrue(f.getAttributes().isEmpty());
        assertTrue(f.existsIndex("as1"));
        f = search.getField("as2");
        assertEquals(f.getAttributes().size(), 1);
        assertTrue(f.existsIndex("as2"));

        f = search.getField("i1");
        assertEquals(f.getAttributes().size(), 1);
        assertTrue( ! f.existsIndex("i1"));

        f = search.getField("i2");
        assertEquals(f.getAttributes().size(), 1);
        assertTrue( ! f.existsIndex("i2"));

        f = search.getField("ai1");
        assertEquals(search.getField("ai1").getAttributes().size(), 1);
        assertTrue( ! search.getField("ai1").existsIndex("ai1"));
        f = search.getField("ai2");
        assertEquals(f.getAttributes().size(), 1);
        assertTrue( ! f.existsIndex("ai2"));
    }
}
