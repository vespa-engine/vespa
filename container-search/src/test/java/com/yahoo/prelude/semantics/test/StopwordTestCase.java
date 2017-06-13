// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.yahoo.search.Query;
import com.yahoo.search.test.QueryTestCase;

/**
 * Tests numeric terms
 *
 * @author bratseth
 */
public class StopwordTestCase extends RuleBaseAbstractTestCase {

    public StopwordTestCase(String name) {
        super(name,"stopwords.sr");
    }

    public void testStopwords() {
        assertSemantics("AND mlr:ve mlr:heard mlr:beautiful mlr:world",
                        new Query(QueryTestCase.httpEncode("?query=i don't know if you've heard, but it's a beautiful world&default-index=mlr&tracelevel.rules=0")));
    }

    public void testStopwordsInPhrase() {
        assertSemantics("AND mlr:\"ve heard\" mlr:beautiful mlr:world",
                new Query(QueryTestCase.httpEncode("?query=\"i don't know if you've heard\", but it's a beautiful world&default-index=mlr&tracelevel.rules=0")));
    }

}
