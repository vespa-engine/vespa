// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        assertSemantics("AND default:primetime default:in default:no default:time", "primetime");
        assertSemantics("AND default:primetime default:in default:no default:time", "prime time in no time");
        assertSemantics("AND default:primetime default:in default:no default:time", "prime time");
        assertSemantics("AND default:primetime default:in default:no default:time", "pint");
        assertSemantics("AND default:primetime default:in default:no default:time", "default:primetime notime");
        assertSemantics("AND default:primetime default:in default:no default:time", "default:primetime");
        assertSemantics("AND default:primetime default:in default:no default:time", "default:prime time in no time");
        assertSemantics("AND default:primetime default:in default:no default:time", "default:prime time");
        assertSemantics("AND default:primetime default:in default:no default:time", "default:pint");
    }

    @Test
    void testCompleteMatchWithNegative() {
        assertSemantics("+(AND default:primetime default:in default:no default:time TRUE) -regionexcl:us",
                new Query(QueryTestCase.httpEncode("?query=primetime ANDNOT regionexcl:us&type=adv")));
    }

    @Test
    void testCompleteMatchWithFilterAndNegative() {
        assertSemantics("AND (+(AND default:primetime default:in default:no default:time TRUE) -regionexcl:us) |lang:en",
                new Query(QueryTestCase.httpEncode("?query=primetime ANDNOT regionexcl:us&type=adv&filter=+lang:en")));
    }

    @Test
    void testInnerMatch() {
        assertSemantics("AND foo default:primetime default:in default:no default:time bar", "foo primetime notime bar");
        assertSemantics("AND foo default:primetime default:in default:no default:time bar", "foo primetime bar");
        assertSemantics("AND foo default:primetime default:in default:no default:time bar", "foo prime time in no time bar");
        assertSemantics("AND foo default:primetime default:in default:no default:time bar", "foo prime time bar");
        assertSemantics("AND foo default:primetime default:in default:no default:time bar", "foo pint bar");
    }
}
