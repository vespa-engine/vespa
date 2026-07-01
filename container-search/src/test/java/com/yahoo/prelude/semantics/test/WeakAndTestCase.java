// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.test.QueryTestCase;
import com.yahoo.search.yql.MinimalQueryInserter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * @author bratseth
 */
public class WeakAndTestCase {

    @Test
    public void testWeakAnd() {
        var tester = new RuleBaseTester("weakAnd.sr", null);
        var query = new Query("?yql=" + QueryTestCase.httpEncode("select * from webpages where (userQuery() and  (hostname contains 'www.legifrance.gouv.fr'))") + "&query=tva");
        search(new SimpleLinguistics(), new IndexModel(Map.of(), List.of()), query);
        tester.assertSemantics("AND (WEAKAND tva taxe sur la valeur ajoutée tva) hostname:'www legifrance gouv fr'", query);
    }

    /** The case that used to NPE: a replacing EQUIV rule on the matched term that is the root weakAnd's lone child. */
    @Test
    public void testReplacingEquivRuleOnSingleTermWeakAndRoot() {
        var tester = new RuleBaseTester("weakAnd.sr", null);
        tester.assertSemantics("EQUIV ig instagram", "ig", 0, Query.Type.WEAKAND);
    }

    @Test
    public void testSingleTargetReplacingEquivOnSingleTermWeakAndRoot() {
        var tester = new RuleBaseTester("weakAnd.sr", null);
        tester.assertSemantics("facebook", "fbb", 0, Query.Type.WEAKAND);
    }

    @Test
    public void testThreeTargetReplacingEquivOnSingleTermWeakAndRoot() {
        var tester = new RuleBaseTester("weakAnd.sr", null);
        tester.assertSemantics("EQUIV a3 b3 c3", "q3", 0, Query.Type.WEAKAND);
    }

    @Test
    public void testPhraseTargetReplacingEquivOnSingleTermWeakAndRoot() {
        var tester = new RuleBaseTester("weakAnd.sr", null);
        tester.assertSemantics("EQUIV nyc \"new york\"", "nyc", 0, Query.Type.WEAKAND);
    }

    @Test
    public void testReplacingEquivOnSingleTermOrRoot() {
        var tester = new RuleBaseTester("weakAnd.sr", null);
        tester.assertSemantics("EQUIV ig instagram", "ig", 0, Query.Type.ANY);
    }

    /** Pre-existing: with siblings the weakAnd isn't emptied, so targets are added flat, not grouped. */
    @Test
    public void testReplacingEquivWithSiblingTermsIsFlattened() {
        var tester = new RuleBaseTester("weakAnd.sr", null);
        tester.assertSemantics("WEAKAND design ig instagram", "ig design", 0, Query.Type.WEAKAND);
    }

    @Test
    public void testReplacingEquivInNestedSoleChildWeakAnd() {
        var tester = new RuleBaseTester("weakAnd.sr", null);
        var query = new Query("?yql=" + QueryTestCase.httpEncode("select * from webpages where (userQuery() and (hostname contains 'site'))") + "&query=ig");
        search(new SimpleLinguistics(), new IndexModel(Map.of(), List.of()), query);
        tester.assertSemantics("AND (EQUIV ig instagram) hostname:site", query);
    }

    /** A later target of a different type joins the first target's container instead of being lost. */
    @Test
    public void testMixedTypeReplacingRuleGroupsAllTargets() {
        var tester = new RuleBaseTester("weakAnd.sr", null);
        tester.assertSemantics("EQUIV foo bar", "mix", 0, Query.Type.WEAKAND);
    }

    @Test
    public void testReplacingRuleNotTargetKeepsNegation() {
        var tester = new RuleBaseTester("weakAnd.sr", null);
        tester.assertSemantics("+foo -bar", "neg", 0, Query.Type.WEAKAND);
    }

    @Test
    public void testReplacingRuleOrTargetKeepsOrContainer() {
        var tester = new RuleBaseTester("weakAnd.sr", null);
        tester.assertSemantics("OR foo bar", "orthr", 0, Query.Type.WEAKAND);
    }

    @Test
    public void testSingleTargetReplacingEquivInNestedSoleChildWeakAnd() {
        var tester = new RuleBaseTester("weakAnd.sr", null);
        var query = new Query("?yql=" + QueryTestCase.httpEncode("select * from webpages where (userQuery() and (hostname contains 'site'))") + "&query=fbb");
        search(new SimpleLinguistics(), new IndexModel(Map.of(), List.of()), query);
        tester.assertSemantics("AND facebook hostname:site", query);
    }

    private Result search(Linguistics linguistics, IndexModel indexModel, Query query) {
        return new Execution(new Chain<>(new MinimalQueryInserter(linguistics) /*, new StemmingSearcher(linguistics)*/),
                             Execution.Context.createContextStub(new IndexFacts(indexModel), linguistics)).search(query);

    }

}
