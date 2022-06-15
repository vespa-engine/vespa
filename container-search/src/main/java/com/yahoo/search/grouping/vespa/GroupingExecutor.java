// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.prelude.fastsearch.GroupingListHit;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.QueryCanonicalizer;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.GroupingValidator;
import com.yahoo.search.grouping.result.Group;
import com.yahoo.search.grouping.result.RootGroup;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.searchlib.aggregation.Grouping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes the {@link GroupingRequest grouping requests} set up by other searchers. This does the necessary
 * transformation from the abstract request to Vespa grouping expressions (using {@link RequestBuilder}), and the
 * corresponding transformation of results (using {@link ResultBuilder}).
 *
 * @author Simon Thoresen Hult
 */
@After({ GroupingValidator.GROUPING_VALIDATED,
         "com.yahoo.search.querytransform.WandSearcher",
         "com.yahoo.search.querytransform.BooleanSearcher" })
@Provides({ GroupingExecutor.COMPONENT_NAME, QueryCanonicalizer.queryCanonicalization } )
public class GroupingExecutor extends Searcher {

    public final static String COMPONENT_NAME = "GroupingExecutor";
    private final static String GROUPING_LIST = "GroupingList";
    private final static CompoundName PROP_GROUPINGLIST = newCompoundName(GROUPING_LIST);
    private final static Logger log = Logger.getLogger(GroupingExecutor.class.getName());

    private static final double DEFAULT_PRECISION_FACTOR = 2.0;
    private static final int DEFAULT_MAX_GROUPS = 10;
    private static final int DEFAULT_MAX_HITS = 10;
    private static final long DEFAULT_GLOBAL_MAX_GROUPS = 10000;

    /**
     * Constructs a new instance of this searcher without configuration.
     * This makes the searcher completely useless for searching purposes,
     * and should only be used for testing its logic.
     */
    GroupingExecutor() {
    }

    /**
     * Constructs a new instance of this searcher with the given component id.
     *
     * @param componentId the identifier to assign to this searcher
     */
    public GroupingExecutor(ComponentId componentId) {
        super(componentId);
    }

    @Override
    public Result search(Query query, Execution execution) {
        String error = QueryCanonicalizer.canonicalize(query);
        if (error != null) return new Result(query, ErrorMessage.createIllegalQuery(error));

        query.prepare();

        if (query.getSelect().getGrouping().isEmpty()) return execution.search(query);

        // Convert requests to Vespa style grouping.
        Map<Integer, Grouping> groupingMap = new HashMap<>();
        List<RequestContext> requestContextList = new LinkedList<>();
        for (int i = 0; i < query.getSelect().getGrouping().size(); i++)
            requestContextList.add(convertRequest(query, query.getSelect().getGrouping().get(i), i, groupingMap));

        if (groupingMap.isEmpty()) return execution.search(query);

        // Perform the necessary passes to execute grouping.
        Result result = performSearch(query, execution, groupingMap);

        // Convert Vespa style results to hits.
        HitConverter hitConverter = new HitConverter(this, query);
        for (RequestContext context : requestContextList) {
            RootGroup group = convertResult(context, groupingMap, hitConverter);
            result.hits().add(group);
        }
        return result;
    }

    @Override
    public void fill(Result result, String summaryClass, Execution execution) {
        Map<String, Result> summaryMap = new HashMap<>();
        for (Iterator<Hit> it = result.hits().unorderedDeepIterator(); it.hasNext(); ) {
            Hit hit = it.next();
            Object metaData = hit.getSearcherSpecificMetaData(this);
            if (metaData instanceof String) {
                // Use the summary class specified by grouping, set in HitConverter, for the first fill request
                // after grouping. This assumes the first fill request is using the default summary class,
                // which may be a fragile assumption. But currently we cannot do better because the difference
                // between explicit and implicit summary class in fill is erased by the Execution.
                // 
                // We reset the summary class here such that following fill calls will execute with the
                // summary class they specify
                summaryClass = (String) metaData;
                hit.setSearcherSpecificMetaData(this, null);
            }
            Result summaryResult = summaryMap.get(summaryClass);
            if (summaryResult == null) {
                summaryResult = new Result(result.getQuery());
                summaryMap.put(summaryClass, summaryResult);
            }
            summaryResult.hits().add(hit);
        }
        for (Map.Entry<String, Result> entry : summaryMap.entrySet()) {
            Result res = entry.getValue();
            execution.fill(res, entry.getKey());
            result.hits().addErrorsFrom(res.hits());
        }
        Result defaultResult = summaryMap.get(ExpressionConverter.DEFAULT_SUMMARY_NAME);
        if (defaultResult != null) {
            // the reason we need to do this fix is that the docsum packet protocol uses null summary class name to
            // signal that the backend should use its configured default, whereas for grouping it uses the literal
            // "default" to signal the same
            for (Hit hit : defaultResult.hits()) {
                hit.setFilled(null);
            }
        }
    }

    /**
     * Converts the given {@link GroupingRequest} into a set of {@link Grouping} objects. The returned object holds the
     * context that corresponds to the given request, whereas the created {@link Grouping} objects are written directly
     * to the given map.
     *
     * @param query the query being executed
     * @param req   the request to convert
     * @param map   the grouping map to write to
     * @return the context required to identify the request results
     */
    private RequestContext convertRequest(Query query, GroupingRequest req, int requestId, Map<Integer, Grouping> map) {
        RequestBuilder builder = new RequestBuilder(requestId);
        builder.setRootOperation(req.getRootOperation());
        builder.setDefaultSummaryName(query.getPresentation().getSummary());
        builder.setTimeZone(req.getTimeZone());
        builder.addContinuations(req.continuations());
        builder.setDefaultMaxGroups(req.defaultMaxGroups().orElse(DEFAULT_MAX_GROUPS));
        builder.setDefaultMaxHits(req.defaultMaxHits().orElse(DEFAULT_MAX_HITS));
        builder.setGlobalMaxGroups(req.globalMaxGroups().orElse(DEFAULT_GLOBAL_MAX_GROUPS));
        builder.setDefaultPrecisionFactor(req.defaultPrecisionFactor().orElse(DEFAULT_PRECISION_FACTOR));
        builder.build();

        RequestContext ctx = new RequestContext(req, builder.getTransform());
        List<Grouping> grpList = builder.getRequestList();
        for (Grouping grp : grpList) {
            int grpId = map.size();
            grp.setId(grpId);
            map.put(grpId, grp);
            ctx.idList.add(grpId);
        }
        return ctx;
    }

    /**
     * Converts the results of the given request context into a single {@link Group}.
     *
     * @param requestContext the context that identifies the results to convert
     * @param groupingMap    the map of all {@link Grouping} objects available
     * @param hitConverter   the converter to use for {@link Hit} conversion
     * @return the corresponding root RootGroup.
     */
    private RootGroup convertResult(RequestContext requestContext, Map<Integer, Grouping> groupingMap,
                                    HitConverter hitConverter) {
        ResultBuilder builder = new ResultBuilder();
        builder.setHitConverter(hitConverter);
        builder.setTransform(requestContext.transform);
        builder.setRequestId(requestContext.request.getRequestId());
        for (Integer grpId : requestContext.idList) {
            builder.addGroupingResult(groupingMap.get(grpId));
        }
        builder.build();
        return builder.getRoot();
    }

    /**
     * Performs the actual search passes to complete all the given {@link Grouping} requests. This method uses the
     * grouping map argument as both an input and an output variable, as the contained {@link Grouping} objects are
     * updates as results arrive from the back end.
     *
     * @param query       the query to execute
     * @param execution   the execution context used to run the queries
     * @param groupingMap the map of grouping requests to perform
     * @return the search result to pass back from this searcher
     */
    private Result performSearch(Query query, Execution execution, Map<Integer, Grouping> groupingMap) {
        // Determine how many passes to perform.
        int lastPass = 0;
        for (Grouping grouping : groupingMap.values()) {
            if ( ! grouping.useSinglePass()) {
                lastPass = Math.max(lastPass, grouping.getLevels().size());
            }
        }

        // Perform multi-pass query to complete all grouping requests.
        Item origRoot = query.getModel().getQueryTree().getRoot();
        Result ret = null;
        Item baseRoot = origRoot;
        if (lastPass > 0) {
            baseRoot = origRoot.clone();
        }
        if (query.getTrace().isTraceable(3) && query.getGroupingSessionCache()) {
            query.trace("Grouping in " + (lastPass + 1) + " passes. SessionId='" + query.getSessionId() + "'.", 3);
        }
        for (int pass = 0; pass <= lastPass; ++pass) {
            boolean firstPass = (pass == 0);
            List<Grouping> passList = getGroupingListForPassN(groupingMap, pass);
            if (passList.isEmpty()) {
                throw new RuntimeException("No grouping request for pass " + pass + ", bug!");
            }
            if (log.isLoggable(Level.FINE)) {
                for (Grouping grouping : passList) {
                    log.log(Level.FINE, "Pass(" + pass + "), Grouping(" + grouping.getId() + "): " + grouping);
                }
            }
            Item passRoot;
            if (firstPass) {
                passRoot = origRoot;     // Use original query the first time.
            } else if (pass == lastPass) {
                passRoot = baseRoot; // Has already been cloned once, use this for last pass.
            } else {
                // noinspection ConstantConditions
                passRoot = baseRoot.clone();
            }
            if (query.getTrace().isTraceable(4) && query.getGroupingSessionCache()) {
                query.trace("Grouping with session cache '" + query.getGroupingSessionCache() + "' enabled for pass #" + pass + ".", 4);
            }
            if (origRoot != passRoot) {
                query.getModel().getQueryTree().setRoot(passRoot);
            }
            setGroupingList(query, passList);
            Result passResult = execution.search(query);
            Map<Integer, Grouping> passGroupingMap = mergeGroupingResults(passResult);
            mergeGroupingMaps(groupingMap, passGroupingMap);
            if (firstPass) {
                ret = passResult;
            } else {
                ret.hits().addErrorsFrom(passResult.hits());
            }
        }
        if (log.isLoggable(Level.FINE)) {
            for (Grouping grouping : groupingMap.values()) {
                log.log(Level.FINE, "Result Grouping(" + grouping.getId() + "): " + grouping);
            }
        }
        return ret;
    }

    /**
     * Merges the content of result into state. This needs to be done in order to conserve the context objects contained
     * in the state as they are not part of the serialized object representation.
     *
     * @param state  the current state
     * @param result the results from the current pass
     */
    private void mergeGroupingMaps(Map<Integer, Grouping> state, Map<Integer, Grouping> result) {
        for (Grouping grouping : result.values()) {
            Grouping old = state.get(grouping.getId());
            if (old != null) {
                old.merge(grouping);
                // no need to invoke postMerge, as state is empty for
                // current level
            } else {
                log.warning("Got grouping result with unknown id: " + grouping);
            }
        }
    }

    /**
     * Returns a list of {@link Grouping} objects that are to be used for the given pass.
     *
     * @param groupingMap the map of all grouping objects
     * @param pass        the pass about to be performed
     * @return a list of grouping objects
     */
    private List<Grouping> getGroupingListForPassN(Map<Integer, Grouping> groupingMap, int pass) {
        List<Grouping> ret = new ArrayList<>();
        for (Grouping grouping : groupingMap.values()) {
            if (grouping.useSinglePass()) {
                if (pass == 0) {
                    grouping.setFirstLevel(0);
                    grouping.setLastLevel(grouping.getLevels().size());
                    ret.add(grouping); // more levels to go
                }
            } else {
                if (pass <= grouping.getLevels().size()) {
                    grouping.setFirstLevel(pass);
                    grouping.setLastLevel(pass);
                    ret.add(grouping); // more levels to go
                }
            }
        }
        return ret;
    }

    /**
     * Merges the grouping content of the given result object. The first grouping hit found by iterating over the result
     * content is kept, and all consecutive matching hits are merged into this.
     *
     * @param result the result to traverse
     * @return a map of merged grouping objects
     */
    private Map<Integer, Grouping> mergeGroupingResults(Result result) {
        Map<Integer, Grouping> ret = new HashMap<>();
        for (Iterator<Hit> i = result.hits().unorderedIterator(); i.hasNext(); ) {
            Hit hit = i.next();
            if (hit instanceof GroupingListHit) {
                for (Grouping grp : ((GroupingListHit)hit).getGroupingList()) {
                    grp.select(
                            o -> o instanceof com.yahoo.searchlib.aggregation.Hit
                                    && ((com.yahoo.searchlib.aggregation.Hit)o).getContext() == null,
                            o -> ((com.yahoo.searchlib.aggregation.Hit)o).setContext(hit));
                    Grouping old = ret.get(grp.getId());
                    if (old != null) {
                        old.merge(grp);
                    } else {
                        ret.put(grp.getId(), grp);
                    }
                }
                i.remove();
            }
        }
        for (Grouping grouping : ret.values()) {
            grouping.postMerge();
        }
        return ret;
    }

    /**
     * Returns the list of {@link Grouping} objects assigned to the given query. If no list has been assigned, this
     * method returns an empty list.
     *
     * @param query the query whose grouping list to return
     * @return the list of assigned grouping objects
     */
    @SuppressWarnings({ "unchecked" })
    public static List<Grouping> getGroupingList(Query query) {
        Object obj = query.properties().get(PROP_GROUPINGLIST);
        if (!(obj instanceof List)) {
            return Collections.emptyList();
        }
        return (List<Grouping>)obj;
    }

    public static boolean hasGroupingList(Query query) {
        Object obj = query.properties().get(PROP_GROUPINGLIST);
        return (obj instanceof List);
    }

    /**
     * Sets the list of {@link Grouping} objects assigned to the given query. This method overwrites any grouping
     * objects already assigned to the query.
     *
     * @param query the query whose grouping list to set
     * @param list   the grouping list to set
     */
    public static void setGroupingList(Query query, List<Grouping> list) {
        query.properties().set(PROP_GROUPINGLIST, list);
    }

    private static CompoundName newCompoundName(String name) {
        return new CompoundName(GroupingExecutor.class.getName() + "." + name);
    }

    private static class RequestContext {

        final List<Integer> idList = new LinkedList<>();
        final GroupingRequest request;
        final GroupingTransform transform;

        RequestContext(GroupingRequest request, GroupingTransform transform) {
            this.request = request;
            this.transform = transform;
        }
    }

}
