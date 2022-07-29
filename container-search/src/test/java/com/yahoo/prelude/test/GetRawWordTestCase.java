// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.test;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests a case reported by MSBE
 *
 * @author bratseth
 */
public class GetRawWordTestCase {

    @Test
    void testGetRawWord() {
        Query query = new Query("?query=%C4%B0%C5%9EBANKASI%20GAZ%C4%B0EM%C4%B0R&type=all&searchChain=vespa");
        assertEquals("AND \u0130\u015EBANKASI GAZ\u0130EM\u0130R", query.getModel().getQueryTree().toString());
        AndItem root = (AndItem) query.getModel().getQueryTree().getRoot();

        {
            WordItem word = (WordItem) root.getItem(0);
            assertEquals("\u0130\u015EBANKASI", word.getRawWord());
            assertEquals(0, word.getOrigin().start);
            assertEquals(9, word.getOrigin().end);
        }

        {
            WordItem word = (WordItem) root.getItem(1);
            assertEquals("GAZ\u0130EM\u0130R", word.getRawWord());
            assertEquals(10, word.getOrigin().start);
            assertEquals(18, word.getOrigin().end);
        }

        assertEquals(18,
                ((WordItem) root.getItem(0)).getOrigin().getSuperstring().length(),
                "Total string is just these words");
    }

}
