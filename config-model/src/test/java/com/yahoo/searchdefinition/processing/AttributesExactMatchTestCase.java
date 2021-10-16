// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.Schema;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.AbstractSchemaTestCase;
import com.yahoo.searchdefinition.document.Matching;
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
        Schema schema = SearchBuilder.buildFromFile("src/test/examples/attributesexactmatch.sd");
        assertEquals(schema.getConcreteField("color").getMatching().getType(), Matching.Type.EXACT);
        assertEquals(schema.getConcreteField("artist").getMatching().getType(), Matching.Type.WORD);
        assertEquals(schema.getConcreteField("drummer").getMatching().getType(), Matching.Type.WORD);
        assertEquals(schema.getConcreteField("guitarist").getMatching().getType(), Matching.Type.TEXT);
        assertEquals(schema.getConcreteField("saxophonist_arr").getMatching().getType(), Matching.Type.WORD);
        assertEquals(schema.getConcreteField("flutist").getMatching().getType(), Matching.Type.TEXT);

        assertFalse(schema.getConcreteField("genre").getMatching().getType().equals(Matching.Type.EXACT));
        assertFalse(schema.getConcreteField("title").getMatching().getType().equals(Matching.Type.EXACT));
        assertFalse(schema.getConcreteField("trumpetist").getMatching().getType().equals(Matching.Type.EXACT));
        assertFalse(schema.getConcreteField("genre").getMatching().getType().equals(Matching.Type.WORD));
        assertFalse(schema.getConcreteField("title").getMatching().getType().equals(Matching.Type.WORD));
        assertFalse(schema.getConcreteField("trumpetist").getMatching().getType().equals(Matching.Type.WORD));

    }

}
