// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.language.opennlp.OpenNlpLinguistics;
import com.yahoo.prelude.semantics.RuleBase;
import com.yahoo.prelude.semantics.RuleBaseException;
import com.yahoo.prelude.semantics.SemanticSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.test.QueryTestCase;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Helper for testing with a rule base.
 * Replace subclassing of RuleBaseAbstractTestCase by this.
 *
 * @author bratseth
 */
public class RuleBaseTester {

    private final String root = "src/test/java/com/yahoo/prelude/semantics/test/rulebases/";
    private final SemanticSearcher searcher;

    public RuleBaseTester(String ruleBaseName) {
        this(ruleBaseName, null);
    }

    public RuleBaseTester(String ruleBaseName, String automataFileName) {
        searcher = createSearcher(ruleBaseName, automataFileName);
    }

    private SemanticSearcher createSearcher(String ruleBaseName, String automataFileName) {
        try {
            if (automataFileName != null)
                automataFileName = root + automataFileName;
            RuleBase ruleBase = RuleBase.createFromFile(root + ruleBaseName, automataFileName, new OpenNlpLinguistics());
            return new SemanticSearcher(ruleBase);
        } catch (Exception e) {
            throw new RuleBaseException("Initialization of rule base '" + ruleBaseName + "' failed", e);
        }
    }

    public Query assertSemantics(String result, String input) {
        return assertSemantics(result, input, 0);
    }

    public Query assertSemantics(String result, String input, int tracelevel) {
        return assertSemantics(result, input, tracelevel, Query.Type.ALL);
    }

    public Query assertSemantics(String result, String input, int tracelevel, Query.Type queryType) {
        Query query = new Query("?query=" + QueryTestCase.httpEncode(input) + "&tracelevel=0&tracelevel.rules=" + tracelevel +
                                "&language=und&type=" + queryType);
        return assertSemantics(result, query);
    }

    public Query assertSemantics(String result, Query query) {
        createExecution(searcher).search(query);
        assertEquals(result, query.getModel().getQueryTree().getRoot().toString());
        return query;
    }

    private Execution createExecution(Searcher searcher) {
        return new Execution(chainedAsSearchChain(searcher), Execution.Context.createContextStub());
    }

    private Chain<Searcher> chainedAsSearchChain(Searcher topOfChain) {
        List<Searcher> searchers = new ArrayList<>();
        searchers.add(topOfChain);
        return new Chain<>(searchers);
    }

}
