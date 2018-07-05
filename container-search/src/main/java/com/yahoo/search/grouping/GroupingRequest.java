// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

import com.yahoo.net.URI;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.grouping.result.Group;
import com.yahoo.search.grouping.result.RootGroup;
import com.yahoo.search.result.Hit;

import java.util.*;

/**
 * An instance of this class represents one of many grouping requests that are attached to a {@link Query}. Use the
 * factory method {@link #newInstance(com.yahoo.search.Query)} to create a new instance of this, then create and set the
 * {@link GroupingOperation} using {@link #setRootOperation(GroupingOperation)}. Once the search returns, access the
 * result {@link Group} using the {@link #getResultGroup(Result)} method.
 *
 * @author Simon Thoresen Hult
 */
public class GroupingRequest {

    private final static CompoundName PROP_REQUEST = new CompoundName(GroupingRequest.class.getName() + ".Request");
    private final List<Continuation> continuations = new ArrayList<>();
    private final int requestId;
    private GroupingOperation root;
    private TimeZone timeZone;
    private URI resultId;

    private GroupingRequest(int requestId) {
        this.requestId = requestId;
    }

    /**
     * Returns the id of this GroupingRequest. This id is injected into the {@link RootGroup} of the final result, and
     * allows tracking of per-request meta data.
     *
     * @return The id of this.
     */
    public int getRequestId() {
        return requestId;
    }

    /**
     * Returns the root {@link GroupingOperation} that defines this request. As long as this remains unset, the request
     * is void.
     *
     * @return The root operation.
     */
    public GroupingOperation getRootOperation() {
        return root;
    }

    /**
     * Sets the root {@link GroupingOperation} that defines this request. As long as this remains unset, the request is
     * void.
     *
     * @param root The root operation to set.
     * @return This, to allow chaining.
     */
    public GroupingRequest setRootOperation(GroupingOperation root) {
        this.root = root;
        return this;
    }

    /**
     * Returns the {@link TimeZone} used when resolving time expressions such as {@link
     * com.yahoo.search.grouping.request.DayOfMonthFunction} and {@link com.yahoo.search.grouping.request.HourOfDayFunction}.
     *
     * @return The time zone in use.
     */
    public TimeZone getTimeZone() {
        return timeZone;
    }

    /**
     * Sets the {@link TimeZone} used when resolving time expressions such as {@link
     * com.yahoo.search.grouping.request.DayOfMonthFunction} and {@link com.yahoo.search.grouping.request.HourOfDayFunction}.
     *
     * @param timeZone The time zone to set.
     * @return This, to allow chaining.
     */
    public GroupingRequest setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
        return this;
    }

    /**
     * Returns the root result {@link RootGroup} that corresponds to this request. This is not available until the
     * search returns. Because searchers are allowed to modify both {@link Result} and {@link Hit} objects freely, this
     * method requires that you pass it the current {@link Result} object as argument.
     *
     * @param result The search result that contains the root group.
     * @return The result {@link RootGroup} of this request, or null if not found.
     */
    public RootGroup getResultGroup(Result result) {
        Hit root = result.hits().get(resultId, -1);
        if (!(root instanceof RootGroup)) {
            return null;
        }
        return (RootGroup)root;
    }

    /**
     * Sets the result {@link RootGroup} of this request. This is used by the executing grouping searcher, and should
     * not be called by a requesting searcher.
     *
     * @param group The result to set.
     * @return This, to allow chaining.
     */
    public GroupingRequest setResultGroup(RootGroup group) {
        this.resultId = group.getId();
        return this;
    }

    /**
     * Returns the list of {@link Continuation}s of this request. This is used by the executing grouping searcher to
     * allow pagination of grouping results.
     *
     * @return The list of Continuations.
     */
    public List<Continuation> continuations() {
        return continuations;
    }

    /**
     * Creates and attaches a new instance of this class to the given {@link Query}. This is necessary to allow {@link
     * #getRequests(Query)} to return all created requests.
     *
     * @param query The query to attach the request to.
     * @return The created request.
     */
    public static GroupingRequest newInstance(Query query) {
        List<GroupingRequest> lst = getRequests(query);
        if (lst.isEmpty()) {
            lst = new LinkedList<>();
            query.properties().set(PROP_REQUEST, lst);
        }
        GroupingRequest ret = new GroupingRequest(lst.size());
        lst.add(ret);
        return ret;
    }

    /**
     * Returns all instances of this class that have been attached to the given {@link Query}. If no requests have been
     * attached to the {@link Query}, this method returns an empty list.
     *
     * @param query The query whose requests to return.
     * @return The list of grouping requests.
     */
    @SuppressWarnings({ "unchecked" })
    public static List<GroupingRequest> getRequests(Query query) {
        Object lst = query.properties().get(PROP_REQUEST);
        if (lst == null) {
            return Collections.emptyList();
        }
        if (!(lst instanceof List)) {
            throw new IllegalArgumentException("Expected " + GroupingRequest.class + ", got " + lst.getClass() + ".");
        }
        return (List<GroupingRequest>)lst;
    }
}
