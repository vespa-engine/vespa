// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import org.junit.jupiter.api.Test;

public class ExpansionTestCase extends RuleBaseAbstractTestCase {

    public ExpansionTestCase() {
        super("expansion.sr");
    }

    @Test
    void testOrExpansion() {
        assertSemantics("OR or1 or2 or3", "or1");
    }

    @Test
    void testEquivExpansion1() {
        assertSemantics("EQUIV equiv1 equiv2 equiv3", "equiv1");
    }

    @Test
    void testEquivExpansion2() {
        assertSemantics("EQUIV testfield:e1 testfield:e2 testfield:e3", "testfield:foo");
    }

    // No equiv: Not optimal, but not wrong either
    @Test
    void testEquivExpansion3() {
        assertSemantics("AND testfield:e1 testfield:e2 testfield:e3 testfield:e1 testfield:e2 testfield:e3",
                        "testfield:foo testfield:bar");
    }

    /** EQUIV production should preserve query structure, not collapse into a root-level EQUIV. */
    @Test
    void testEquivPreservesQueryStructure() {
        assertSemantics("AND red running (EQUIV equiv1 equiv2 equiv3)", "red running equiv1");
    }

    /** EQUIV at the start of a multi-term query should also preserve structure. */
    @Test
    void testEquivPreservesQueryStructureMatchFirst() {
        assertSemantics("AND (EQUIV equiv1 equiv2 equiv3) is great", "equiv1 is great");
    }

    /** EQUIV in the middle of a multi-term query should also preserve structure. */
    @Test
    void testEquivPreservesQueryStructureMatchMiddle() {
        assertSemantics("AND foo (EQUIV equiv1 equiv2 equiv3) bar", "foo equiv1 bar");
    }

    /** Cascading EQUIV: rule 1 produces cascade2, rule 2 matches it and adds cascade3 to the same EQUIV. */
    @Test
    void testEquivCascading() {
        assertSemantics("EQUIV cascade1 cascade2 cascade3", "cascade1");
    }

    /** Cascading EQUIV in a multi-term query should preserve structure. */
    @Test
    void testEquivCascadingPreservesQueryStructure() {
        assertSemantics("AND foo (EQUIV cascade1 cascade2 cascade3) bar", "foo cascade1 bar");
    }

    // Multi-word condition with unquoted target
    @Test
    void testEquivMultiWordCondition() {
        assertSemantics("EQUIV \"multi1 multi2\" expanded", "multi1 multi2");
    }

    @Test
    void testEquivMultiWordConditionInLongerQuery() {
        assertSemantics("AND foo (EQUIV \"multi1 multi2\" expanded) bar", "foo multi1 multi2 bar");
    }

    @Test
    void testEquivMultiWordConditionMultipleTargets() {
        assertSemantics("AND foo (EQUIV \"multi3 multi4\" expanded3 expanded4) bar", "foo multi3 multi4 bar");
    }

    @Test
    void testEquivMultiWordConditionQuotedTarget() {
        assertSemantics("EQUIV \"multi5 multi6\" quotedexpanded", "multi5 multi6");
    }

    @Test
    void testEquivMultiWordConditionQuotedTargetInLongerQuery() {
        assertSemantics("AND foo (EQUIV \"multi5 multi6\" quotedexpanded) bar", "foo multi5 multi6 bar");
    }

    // Multi-word condition with quoted multi-word (phrase) target
    @Test
    void testEquivMultiWordConditionQuotedPhraseTarget() {
        assertSemantics("EQUIV \"multi7 multi8\" \"quoted two words\"", "multi7 multi8");
    }

    @Test
    void testEquivMultiWordConditionQuotedPhraseTargetInLongerQuery() {
        assertSemantics("AND foo (EQUIV \"multi7 multi8\" \"quoted two words\") bar", "foo multi7 multi8 bar");
    }

    // Mixed quoted and unquoted targets from a multi-word condition
    @Test
    void testEquivMultiWordConditionMixedTargets() {
        assertSemantics("AND foo (EQUIV \"mw1 mw2\" single1 single2) bar", "foo mw1 mw2 bar");
    }

    // Single-word condition with mixed quoted phrase + unquoted targets
    @Test
    void testEquivMixedQuotedAndUnquotedTargets() {
        assertSemantics("AND foo (EQUIV single1 \"mw1 mw2\" single2) bar", "foo single1 bar");
    }

    @Test
    void testEquivMixedQuotedAndUnquotedTargetsReverse() {
        assertSemantics("AND foo (EQUIV single2 \"mw1 mw2\" single1) bar", "foo single2 bar");
    }

    // Multiple quoted targets in a single rule
    @Test
    void testEquivMultipleQuotedTargets() {
        assertSemantics("EQUIV phrase1 \"target one\" \"target two\"", "phrase1");
    }

    @Test
    void testEquivMultipleQuotedTargetsInLongerQuery() {
        assertSemantics("AND foo (EQUIV phrase1 \"target one\" \"target two\") bar", "foo phrase1 bar");
    }

    // Mixed unquoted and quoted targets in a single rule
    @Test
    void testEquivUnquotedThenQuotedTarget() {
        assertSemantics("AND foo (EQUIV phrase2 unquoted \"quoted phrase\") bar", "foo phrase2 bar");
    }

}
