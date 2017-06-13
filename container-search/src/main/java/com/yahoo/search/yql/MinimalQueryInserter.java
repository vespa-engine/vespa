// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.google.common.annotations.Beta;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.query.parser.ParserFactory;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;
import com.yahoo.yolean.chain.After;
import com.yahoo.yolean.chain.Before;
import com.yahoo.yolean.chain.Provides;

/**
 * Minimal combinator for YQL+ syntax and heuristically parsed user queries.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 * @since 5.1.28
 */
@Beta
@Provides(MinimalQueryInserter.EXTERNAL_YQL)
@Before(PhaseNames.TRANSFORMED_QUERY)
@After("com.yahoo.prelude.statistics.StatisticsSearcher")
public class MinimalQueryInserter extends Searcher {
    public static final String EXTERNAL_YQL = "ExternalYql";

    public static final CompoundName YQL = new CompoundName("yql");

    private static final CompoundName MAX_HITS = new CompoundName("maxHits");
    private static final CompoundName MAX_OFFSET = new CompoundName("maxOffset");

    public MinimalQueryInserter() {
    }

    @Override
    public Result search(Query query, Execution execution) {
        if (query.properties().get(YQL) == null) {
            return execution.search(query);
        }
        ParserEnvironment env = ParserEnvironment.fromExecutionContext(execution.context());
        YqlParser parser = (YqlParser) ParserFactory.newInstance(Query.Type.YQL, env);
        parser.setQueryParser(false);
        parser.setUserQuery(query);
        QueryTree newTree;
        try {
            newTree = parser.parse(Parsable.fromQueryModel(query.getModel())
                                           .setQuery(query.properties().getString(YQL)));
        } catch (RuntimeException e) {
            return new Result(query, ErrorMessage.createInvalidQueryParameter(
                              "Could not instantiate query from YQL", e));
        }
        if (parser.getOffset() != null) {
            int maxHits = query.properties().getInteger(MAX_HITS);
            int maxOffset = query.properties().getInteger(MAX_OFFSET);
            if (parser.getOffset() > maxOffset) {
                return new Result(query, ErrorMessage.createInvalidQueryParameter("Requested offset " + parser.getOffset()
                                                                                  + ", but the max offset allowed is " + 
                                                                                  maxOffset + "."));
            }
            if (parser.getHits() > maxHits) {
                return new Result(query, ErrorMessage.createInvalidQueryParameter("Requested " + parser.getHits()
                                                                                  + " hits returned, but max hits allowed is " 
                                                                                  + maxHits + "."));

            }
        }
        query.getModel().getQueryTree().setRoot(newTree.getRoot());
        query.getPresentation().getSummaryFields().addAll(parser.getYqlSummaryFields());
        for (VespaGroupingStep step : parser.getGroupingSteps()) {
            GroupingRequest.newInstance(query)
                           .setRootOperation(step.getOperation())
                           .continuations().addAll(step.continuations());
        }
        if (parser.getYqlSources().size() == 0) {
            query.getModel().getSources().clear();
        } else {
            query.getModel().getSources().addAll(parser.getYqlSources());
        }
        if (parser.getOffset() != null) {
            query.setOffset(parser.getOffset());
            query.setHits(parser.getHits());
        }
        if (parser.getTimeout() != null) {
            query.setTimeout(parser.getTimeout().longValue());
        }
        if (parser.getSorting() != null) {
            query.getRanking().setSorting(parser.getSorting());
        }
        query.trace("YQL+ query parsed", true, 2);
        return execution.search(query);
    }

}
