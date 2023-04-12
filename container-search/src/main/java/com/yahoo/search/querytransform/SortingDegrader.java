// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.QueryCanonicalizer;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.GroupingQueryParser;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.query.Sorting;
import com.yahoo.search.query.properties.DefaultProperties;
import com.yahoo.search.query.ranking.MatchPhase;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.yolean.chain.After;
import com.yahoo.yolean.chain.Before;

import java.util.List;
import java.util.Set;

/**
 * If the query is eligible, specify that the query should degrade if it causes too many hits
 * to avoid excessively expensive queries.
 * <p>
 * Queries are eligible if they do sorting, don't do grouping, and the first sort criteria is a fast-search attribute.
 *
 * @author bratseth
 */
// This writes fields to query.getRanking which are moved to rank.properties during query.prepare()
// Query.prepare is done at the same time as canonicalization (by GroupingExecutor), so use that constraint.
// (we're not adding another constraint at this point because all this preparation and encoding business
// should be fixed when we move to Slime for serialization. - Jon, in the spring of the year of 2014)
@Before(QueryCanonicalizer.queryCanonicalization)

// We are checking if there is a grouping expression, not if there is a raw grouping instruction property,
// so we must run after the property is transferred to a grouping expression
@After(GroupingQueryParser.SELECT_PARAMETER_PARSING)
public class SortingDegrader extends Searcher {

    /** Set this to false in query.properties to turn off degrading. Default: on */
    // (this is not called ranking.sorting.degrading because it should not be part of the query object model
    public static final CompoundName DEGRADING = CompoundName.from("sorting.degrading");

    public static final CompoundName PAGINATION = CompoundName.from("to_be_removed_pagination");

    @Override
    public Result search(Query query, Execution execution) {
        if (shouldBeDegraded(query, execution.context().getIndexFacts().newSession(query)))
            setDegradation(query);
        return execution.search(query);
    }

    private boolean shouldBeDegraded(Query query, IndexFacts.Session indexFacts) {
        if (query.getRanking().getSorting() == null) return false;
        if (query.getRanking().getSorting().fieldOrders().isEmpty()) return false;
        if ( ! query.getSelect().getGrouping().isEmpty()) return false;
        if ( ! query.properties().getBoolean(DEGRADING, true)) return false;

        Index index = indexFacts.getIndex(query.getRanking().getSorting().fieldOrders().get(0).getFieldName());
        if (index == null) return false;
        if ( ! index.isFastSearch()) return false;
        if ( ! index.isNumerical()) return false;

        return true;
    }

    private void setDegradation(Query query) {
        query.trace("Using sorting degrading for performance - totalHits will be wrong. " + 
                    "Turn off with sorting.degrading=false.", 2);
        Sorting.FieldOrder primarySort = query.getRanking().getSorting().fieldOrders().get(0); // ensured above
        MatchPhase matchPhase = query.getRanking().getMatchPhase();

        matchPhase.setAttribute(primarySort.getFieldName());
        matchPhase.setAscending(primarySort.getSortOrder() == Sorting.Order.ASCENDING);
        if (matchPhase.getMaxHits() == null)
            matchPhase.setMaxHits(decideDefaultMaxHits(query));
    }

    /**
     * Look at a "reasonable" number of this by default. We don't want to set this too low because it impacts
     * the totalHits value returned.
     * <p>
     * If maxhits/offset is set high, use that as the default instead because it means somebody will want to be able to
     * get lots of hits. We could use hits+offset instead of maxhits+maxoffset but that would destroy pagination
     * with large values because totalHits is wrong.
     * <p>
     * If we ever get around to estimate totalhits we can rethink this.
     */
    private long decideDefaultMaxHits(Query query) {
        int maxHits;
        int maxOffset;
        if (query.properties().getBoolean(PAGINATION, true)) {
            maxHits = query.properties().getInteger(DefaultProperties.MAX_HITS);
            maxOffset = query.properties().getInteger(DefaultProperties.MAX_OFFSET);
        } else {
            maxHits = query.getHits();
            maxOffset = query.getOffset();
        }
        return maxHits + maxOffset;
    }

}

