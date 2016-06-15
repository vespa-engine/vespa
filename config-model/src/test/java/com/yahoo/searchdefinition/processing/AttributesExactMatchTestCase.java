// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
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
public class AttributesExactMatchTestCase extends SearchDefinitionTestCase {
    @Test
    public void testAttributesExactMatch() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/attributesexactmatch.sd");
        assertEquals(search.getField("color").getMatching().getType(), Matching.Type.EXACT);
        assertEquals(search.getField("artist").getMatching().getType(), Matching.Type.WORD);
        assertEquals(search.getField("drummer").getMatching().getType(), Matching.Type.WORD);
        assertEquals(search.getField("guitarist").getMatching().getType(), Matching.Type.TEXT);
        assertEquals(search.getField("saxophonist_arr").getMatching().getType(), Matching.Type.WORD);
        assertEquals(search.getField("flutist").getMatching().getType(), Matching.Type.TEXT);

        assertFalse(search.getField("genre").getMatching().getType().equals(Matching.Type.EXACT));
        assertFalse(search.getField("title").getMatching().getType().equals(Matching.Type.EXACT));
        assertFalse(search.getField("trumpetist").getMatching().getType().equals(Matching.Type.EXACT));
        assertFalse(search.getField("genre").getMatching().getType().equals(Matching.Type.WORD));
        assertFalse(search.getField("title").getMatching().getType().equals(Matching.Type.WORD));
        assertFalse(search.getField("trumpetist").getMatching().getType().equals(Matching.Type.WORD));

    }

}
