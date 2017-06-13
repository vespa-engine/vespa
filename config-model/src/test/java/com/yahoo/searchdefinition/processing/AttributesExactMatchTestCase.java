// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        assertEquals(search.getConcreteField("color").getMatching().getType(), Matching.Type.EXACT);
        assertEquals(search.getConcreteField("artist").getMatching().getType(), Matching.Type.WORD);
        assertEquals(search.getConcreteField("drummer").getMatching().getType(), Matching.Type.WORD);
        assertEquals(search.getConcreteField("guitarist").getMatching().getType(), Matching.Type.TEXT);
        assertEquals(search.getConcreteField("saxophonist_arr").getMatching().getType(), Matching.Type.WORD);
        assertEquals(search.getConcreteField("flutist").getMatching().getType(), Matching.Type.TEXT);

        assertFalse(search.getConcreteField("genre").getMatching().getType().equals(Matching.Type.EXACT));
        assertFalse(search.getConcreteField("title").getMatching().getType().equals(Matching.Type.EXACT));
        assertFalse(search.getConcreteField("trumpetist").getMatching().getType().equals(Matching.Type.EXACT));
        assertFalse(search.getConcreteField("genre").getMatching().getType().equals(Matching.Type.WORD));
        assertFalse(search.getConcreteField("title").getMatching().getType().equals(Matching.Type.WORD));
        assertFalse(search.getConcreteField("trumpetist").getMatching().getType().equals(Matching.Type.WORD));

    }

}
