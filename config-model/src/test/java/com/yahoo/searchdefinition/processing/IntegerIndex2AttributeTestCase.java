// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.SchemaTestCase;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author baldersheim
 */
public class IntegerIndex2AttributeTestCase extends SchemaTestCase {

    @Test
    public void testIntegerIndex2Attribute() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/integerindex2attribute.sd");
        new IntegerIndex2Attribute(search, new BaseDeployLogger(), new RankProfileRegistry(), new QueryProfiles()).process(true, false);

        SDField f;
        f = search.getConcreteField("s1");
        assertTrue(f.getAttributes().isEmpty());
        assertTrue(f.existsIndex("s1"));
        f = search.getConcreteField("s2");
        assertEquals(f.getAttributes().size(), 1);
        assertTrue(f.existsIndex("s2"));

        f = search.getConcreteField("as1");
        assertTrue(f.getAttributes().isEmpty());
        assertTrue(f.existsIndex("as1"));
        f = search.getConcreteField("as2");
        assertEquals(f.getAttributes().size(), 1);
        assertTrue(f.existsIndex("as2"));

        f = search.getConcreteField("i1");
        assertEquals(f.getAttributes().size(), 1);
        assertFalse(f.existsIndex("i1"));

        f = search.getConcreteField("i2");
        assertEquals(f.getAttributes().size(), 1);
        assertFalse(f.existsIndex("i2"));

        f = search.getConcreteField("ai1");
        assertEquals(search.getConcreteField("ai1").getAttributes().size(), 1);
        assertFalse(search.getConcreteField("ai1").existsIndex("ai1"));
        f = search.getConcreteField("ai2");
        assertEquals(f.getAttributes().size(), 1);
        assertFalse(f.existsIndex("ai2"));
    }

}
