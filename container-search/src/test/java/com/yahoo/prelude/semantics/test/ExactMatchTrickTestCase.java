// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.yahoo.search.Query;
import com.yahoo.search.test.QueryTestCase;

/**
 * @author bratseth
 */
public class ExactMatchTrickTestCase extends RuleBaseAbstractTestCase {

    public ExactMatchTrickTestCase(String name) {
        super(name,"exactmatchtrick.sr");
    }

    public void testCompleteMatch() {
        assertSemantics("AND default:primetime default:in default:no default:time", "primetime notime");
    }

    public void testCompleteMatchWithNegative() { // Notice ordering bug
        assertSemantics("+(AND default:primetime default:in default:time default:no) -regionexcl:us",
                        new Query(QueryTestCase.httpEncode("?query=primetime ANDNOT regionexcl:us&type=adv")));
    }

    public void testCompleteMatchWithFilterAndNegative() {
        assertSemantics("AND (+(AND default:primetime default:in default:time default:no) -regionexcl:us) |lang:en",
                        new Query(QueryTestCase.httpEncode("?query=primetime ANDNOT regionexcl:us&type=adv&filter=+lang:en")));
    }

}
