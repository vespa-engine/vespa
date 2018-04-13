// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.Test;

/**
 * Experiments with a way to match only if it doesn't remove all hard conditions in the query.
 * The problem is that a straightforward use case of replacement leads to nonsensical queries as shown.
 *
 * @author bratseth
 */
public class MatchOnlyIfNotOnlyTermTestCase extends RuleBaseAbstractTestCase {

    public MatchOnlyIfNotOnlyTermTestCase() {
        super("match-only-if-not-only-term.sr");
    }

    @Test
    public void testMatch() {
        assertSemantics("RANK (AND justin timberlake) showname:\"saturday night live\"!1000", "justin timberlake snl");
        assertSemantics("RANK (AND justin timberlake) showname:\"saturday night live\"!1000", "justin timberlake saturday night live");
    }

    @Test
    public void testNoMatch() {
        // TODO: This shows that we do match, i.e that currently the behavior is undesired
        assertSemantics("showname:\"saturday night live\"!1000", "snl");
        assertSemantics("showname:\"saturday night live\"!1000", "saturday night live");
    }

}
