// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.debug.test;

import junit.framework.TestCase;

import com.yahoo.search.debug.SearchChainTextRepresentation;
import com.yahoo.search.searchchain.SearchChainRegistry;
import com.yahoo.search.searchchain.test.SimpleSearchChain;

/**
 * Test of SearchChainTextRepresentation.
 * @author tonytv
 */
public class SearchChainTextRepresentationTestCase extends TestCase {

    public void testTextRepresentation() {
        SearchChainTextRepresentation textRepresentation =
                new SearchChainTextRepresentation(SimpleSearchChain.orderedChain, new SearchChainRegistry());

        String[] expected = {
                "test [Searchchain]  {",
                "  one [Searcher] {",
                "    Reason for forwarding to this search chain.",
                "    child-chain [Searchchain]  {",
                "      child-searcher [Searcher]",
                "    }",
                "    child-chain [Searchchain]  {",
                "      child-searcher [Searcher]",
                "    }",
                "  }",
                "  two [Searcher] {",
                "    Reason for forwarding to this search chain.",
                "    child-chain [Searchchain]  {",
                "      child-searcher [Searcher]",
                "    }",
                "    child-chain [Searchchain]  {",
                "      child-searcher [Searcher]",
                "    }",
                "  }",
                "}"
        };

        String[] result = textRepresentation.toString().split("\n");
        assertEquals(expected.length, result.length);

        int i = 0;
        for (String line :  textRepresentation.toString().split("\n"))
            assertEquals(expected[i++], line);
    }

}
