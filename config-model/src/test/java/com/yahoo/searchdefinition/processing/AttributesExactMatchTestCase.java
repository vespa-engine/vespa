// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.Schema;
import com.yahoo.searchdefinition.ApplicationBuilder;
import com.yahoo.searchdefinition.AbstractSchemaTestCase;
import com.yahoo.searchdefinition.document.Matching;
import com.yahoo.searchdefinition.document.MatchType;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
/**
 * Attributes should be implicitly exact-match in some cases
 * @author vegardh
 *
 */
public class AttributesExactMatchTestCase extends AbstractSchemaTestCase {
    @Test
    public void testAttributesExactMatch() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/attributesexactmatch.sd");
        assertEquals(schema.getConcreteField("color").getMatching().getType(), MatchType.EXACT);
        assertEquals(schema.getConcreteField("artist").getMatching().getType(), MatchType.WORD);
        assertEquals(schema.getConcreteField("drummer").getMatching().getType(), MatchType.WORD);
        assertEquals(schema.getConcreteField("guitarist").getMatching().getType(), MatchType.TEXT);
        assertEquals(schema.getConcreteField("saxophonist_arr").getMatching().getType(), MatchType.WORD);
        assertEquals(schema.getConcreteField("flutist").getMatching().getType(), MatchType.TEXT);

        assertFalse(schema.getConcreteField("genre").getMatching().getType().equals(MatchType.EXACT));
        assertFalse(schema.getConcreteField("title").getMatching().getType().equals(MatchType.EXACT));
        assertFalse(schema.getConcreteField("trumpetist").getMatching().getType().equals(MatchType.EXACT));
        assertFalse(schema.getConcreteField("genre").getMatching().getType().equals(MatchType.WORD));
        assertFalse(schema.getConcreteField("title").getMatching().getType().equals(MatchType.WORD));
        assertFalse(schema.getConcreteField("trumpetist").getMatching().getType().equals(MatchType.WORD));

    }

}
