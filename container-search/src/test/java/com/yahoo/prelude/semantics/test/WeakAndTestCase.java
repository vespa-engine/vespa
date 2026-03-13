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

    private Result search(Linguistics linguistics, IndexModel indexModel, Query query) {
        return new Execution(new Chain<>(new MinimalQueryInserter(linguistics) /*, new StemmingSearcher(linguistics)*/),
                             Execution.Context.createContextStub(new IndexFacts(indexModel), linguistics)).search(query);

    }

}
