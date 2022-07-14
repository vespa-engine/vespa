// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.yahoo.api.annotations.Beta;
import com.yahoo.component.annotation.Inject;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.grouping.GroupingQueryParser;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.query.parser.ParserFactory;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;
import com.yahoo.yolean.Exceptions;
import com.yahoo.yolean.chain.After;
import com.yahoo.yolean.chain.Before;
import com.yahoo.yolean.chain.Provides;

import java.util.logging.Logger;

/**
 * Minimal combinator for YQL+ syntax and heuristically parsed user queries.
 *
 * @author Steinar Knutsen
 */
// TODO: The query model should do this
@Beta
@Provides(MinimalQueryInserter.EXTERNAL_YQL)
@Before(PhaseNames.TRANSFORMED_QUERY)
@After("com.yahoo.prelude.statistics.StatisticsSearcher")
public class MinimalQueryInserter extends Searcher {

    public static final String EXTERNAL_YQL = "ExternalYql";

    public static final CompoundName YQL = new CompoundName("yql");

    private static final CompoundName MAX_HITS = new CompoundName("maxHits");
    private static final CompoundName MAX_OFFSET = new CompoundName("maxOffset");
    private static final Logger log = Logger.getLogger(MinimalQueryInserter.class.getName());

    @Inject
    public MinimalQueryInserter(Linguistics linguistics) {
        // Warmup is needed to avoid a large 400ms init cost during first execution of yql code.
        warmup(linguistics);
    }

    public MinimalQueryInserter() {
        this(new SimpleLinguistics());
    }

    static boolean warmup() {
        return warmup(new SimpleLinguistics());
    }

    private static boolean warmup(Linguistics linguistics) {
        Query query = new Query("search/?yql=select%20*%20from%20sources%20where%20title%20contains%20'xyz'");
        Result result = insertQuery(query, new ParserEnvironment().setLinguistics(linguistics));
        if (result != null) {
            log.warning("Warmup code trigger an error. Error = " + result);
            return false;
        }
        if ( ! "select * from sources where title contains \"xyz\"".equals(query.yqlRepresentation())) {
            log.warning("Warmup code generated unexpected yql: " + query.yqlRepresentation());
            return false;
        }
        return true;
    }

    @Override
    public Result search(Query query, Execution execution) {
        try {
            if (query.properties().get(YQL) == null) return execution.search(query);
            Result result = insertQuery(query, ParserEnvironment.fromExecutionContext(execution.context()));
            return (result == null) ? execution.search(query) : result;
        }
        catch (IllegalArgumentException e) {
            throw new IllegalInputException("Illegal YQL query", e);
        }
    }

    private static Result insertQuery(Query query, ParserEnvironment env) {
        YqlParser parser = (YqlParser) ParserFactory.newInstance(Query.Type.YQL, env);
        parser.setQueryParser(false);
        parser.setUserQuery(query);
        QueryTree newTree;
        try {
            Parsable parsable = Parsable.fromQueryModel(query.getModel()).setQuery(query.properties().getString(YQL));
            newTree = parser.parse(parsable);
        } catch (RuntimeException e) {
            return new Result(query, ErrorMessage.createInvalidQueryParameter("Could not create query from YQL: " +
                                                                              Exceptions.toMessageString(e),
                                                                              e));
        }
        if (parser.getOffset() != null) {
            int maxHits = query.properties().getInteger(MAX_HITS);
            int maxOffset = query.properties().getInteger(MAX_OFFSET);
            if (parser.getOffset() > maxOffset) {
                return new Result(query,
                                  ErrorMessage.createInvalidQueryParameter("Requested offset " + parser.getOffset() +
                                                                           ", but the max offset allowed is " +
                                                                           maxOffset + "."));
            }
            if (parser.getHits() > maxHits) {
                return new Result(query,
                                  ErrorMessage.createInvalidQueryParameter("Requested " + parser.getHits() +
                                                                           " hits returned, but max hits allowed is " +
                                                                           maxHits + "."));
            }
        }
        query.getModel().getQueryTree().setRoot(newTree.getRoot());
        query.getPresentation().getSummaryFields().addAll(parser.getYqlSummaryFields());

        GroupingQueryParser.validate(query);
        for (VespaGroupingStep step : parser.getGroupingSteps())
            GroupingQueryParser.createGroupingRequestIn(query, step.getOperation(), step.continuations());

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
        return null;
    }

}
