// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.yahoo.search.Query;
import com.yahoo.search.test.QueryTestCase;
import org.junit.jupiter.api.Test;

/**
 * @author bratseth
 */
public class ExactMatchTrickTestCase extends RuleBaseAbstractTestCase {

    public ExactMatchTrickTestCase() {
        super("exactmatchtrick.sr");
    }

    @Test
    void testCompleteMatch() {
        assertSemantics("AND default:primetime default:in default:no default:time", "primetime notime");
    }

    @Test
    void testCompleteMatchWithNegative() { // Notice ordering bug
        assertSemantics("+(AND default:primetime default:in default:time default:no TRUE) -regionexcl:us",
                new Query(QueryTestCase.httpEncode("?query=primetime ANDNOT regionexcl:us&type=adv")));
    }

    @Test
    void testCompleteMatchWithFilterAndNegative() {
        assertSemantics("AND (+(AND default:primetime default:in default:time default:no TRUE) -regionexcl:us) |lang:en",
                new Query(QueryTestCase.httpEncode("?query=primetime ANDNOT regionexcl:us&type=adv&filter=+lang:en")));
    }

}
