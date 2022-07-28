// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author baldersheim
 */
public class IntegerIndex2AttributeTestCase extends AbstractSchemaTestCase {

    @Test
    void testIntegerIndex2Attribute() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/integerindex2attribute.sd");
        new IntegerIndex2Attribute(schema, new BaseDeployLogger(), new RankProfileRegistry(), new QueryProfiles()).process(true, false);

        SDField f;
        f = schema.getConcreteField("s1");
        assertTrue(f.getAttributes().isEmpty());
        assertTrue(f.existsIndex("s1"));
        f = schema.getConcreteField("s2");
        assertEquals(f.getAttributes().size(), 1);
        assertTrue(f.existsIndex("s2"));

        f = schema.getConcreteField("as1");
        assertTrue(f.getAttributes().isEmpty());
        assertTrue(f.existsIndex("as1"));
        f = schema.getConcreteField("as2");
        assertEquals(f.getAttributes().size(), 1);
        assertTrue(f.existsIndex("as2"));

        f = schema.getConcreteField("i1");
        assertEquals(f.getAttributes().size(), 1);
        assertFalse(f.existsIndex("i1"));

        f = schema.getConcreteField("i2");
        assertEquals(f.getAttributes().size(), 1);
        assertFalse(f.existsIndex("i2"));

        f = schema.getConcreteField("ai1");
        assertEquals(schema.getConcreteField("ai1").getAttributes().size(), 1);
        assertFalse(schema.getConcreteField("ai1").existsIndex("ai1"));
        f = schema.getConcreteField("ai2");
        assertEquals(f.getAttributes().size(), 1);
        assertFalse(f.existsIndex("ai2"));
    }

}
