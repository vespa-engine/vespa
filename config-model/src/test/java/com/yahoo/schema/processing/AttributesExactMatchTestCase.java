// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.document.MatchType;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Attributes should be implicitly exact-match in some cases
 * @author vegardh
 *
 */
public class AttributesExactMatchTestCase extends AbstractSchemaTestCase {
    @Test
    void testAttributesExactMatch() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/attributesexactmatch.sd");
        assertEquals(schema.getConcreteField("color").getMatching().getType(), MatchType.EXACT);
        assertEquals(schema.getConcreteField("artist").getMatching().getType(), MatchType.WORD);
        assertEquals(schema.getConcreteField("drummer").getMatching().getType(), MatchType.WORD);
        assertEquals(schema.getConcreteField("guitarist").getMatching().getType(), MatchType.TEXT);
        assertEquals(schema.getConcreteField("saxophonist_arr").getMatching().getType(), MatchType.WORD);
        assertEquals(schema.getConcreteField("flutist").getMatching().getType(), MatchType.TEXT);

        assertNotEquals(schema.getConcreteField("genre").getMatching().getType(), MatchType.EXACT);
        assertNotEquals(schema.getConcreteField("title").getMatching().getType(), MatchType.EXACT);
        assertNotEquals(schema.getConcreteField("trumpetist").getMatching().getType(), MatchType.EXACT);
        assertNotEquals(schema.getConcreteField("genre").getMatching().getType(), MatchType.WORD);
        assertNotEquals(schema.getConcreteField("title").getMatching().getType(), MatchType.WORD);
        assertNotEquals(schema.getConcreteField("trumpetist").getMatching().getType(), MatchType.WORD);

    }

}
