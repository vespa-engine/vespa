// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.yahoo.search.Query;
import com.yahoo.search.test.QueryTestCase;
import org.junit.jupiter.api.Test;

/**
 * Tests numeric terms
 *
 * @author bratseth
 */
public class StopwordTestCase extends RuleBaseAbstractTestCase {

    public StopwordTestCase() {
        super("stopwords.sr");
    }

    @Test
    void testStopwords() {
        assertSemantics("WEAKAND(100) mlr:ve mlr:heard mlr:beautiful mlr:world",
                new Query(QueryTestCase.httpEncode("?query=i don't know if you've heard, but it's a beautiful world&default-index=mlr&tracelevel.rules=0")));
    }

    /** If the query contains nothing but stopwords, we won't remove them */
    @Test
    void testOnlyStopwords() {
        assertSemantics("WEAKAND(100) mlr:the", new Query(QueryTestCase.httpEncode("?query=the the&default-index=mlr&tracelevel.rules=0")));
    }

    @Test
    void testStopwordsInPhrase() {
        assertSemantics("WEAKAND(100) mlr:\"ve heard\" mlr:beautiful mlr:world",
                new Query(QueryTestCase.httpEncode("?query=\"i don't know if you've heard\", but it's a beautiful world&default-index=mlr&tracelevel.rules=0")));
    }

}
