// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

import com.yahoo.api.annotations.Beta;
import com.yahoo.net.URI;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.grouping.result.Group;
import com.yahoo.search.grouping.result.RootGroup;
import com.yahoo.search.grouping.result.RootId;
import com.yahoo.search.query.Select;
import com.yahoo.search.result.Hit;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.TimeZone;

/**
 * An instance of this class represents one of many grouping requests that are attached to a {@link Query}. Use the
 * factory method {@link #newInstance(com.yahoo.search.Query)} to create a new instance of this, then create and set the
 * {@link GroupingOperation} using {@link #setRootOperation(GroupingOperation)}. Once the search returns, access the
 * result {@link Group} using the {@link #getResultGroup(Result)} method.
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public class GroupingRequest {

    private final Select parent;
    private final List<Continuation> continuations = new ArrayList<>();
    private GroupingOperation root;
    private TimeZone timeZone;
    private Integer defaultMaxHits;
    private Integer defaultMaxGroups;
    private Long globalMaxGroups;
    private Double defaultPrecisionFactor;

    private GroupingRequest(Select parent) {
        this.parent = parent;
    }

    private GroupingRequest(Select parent,
                            List<Continuation> continuations,
                            GroupingOperation root,
                            TimeZone timeZone,
                            Integer defaultMaxHits,
                            Integer defaultMaxGroups,
                            Long globalMaxGroups,
                            Double defaultPrecisionFactor) {
        this.parent = parent;
        continuations.forEach(item -> this.continuations.add(item.copy()));
        this.root = root != null ? root.copy(null) : null;
        this.timeZone = timeZone;
        this.defaultMaxHits = defaultMaxHits;
        this.defaultMaxGroups = defaultMaxGroups;
        this.globalMaxGroups = globalMaxGroups;
        this.defaultPrecisionFactor = defaultPrecisionFactor;
    }

    /** Returns a deep copy of this */
    public GroupingRequest copy(Select parentOfCopy) {
        return new GroupingRequest(parentOfCopy, continuations, root, timeZone, defaultMaxHits, defaultMaxGroups,
                globalMaxGroups, defaultPrecisionFactor);
    }

    /**
     * Returns the id of this GroupingRequest.
     * This id is injected into the {@link RootGroup} of the final result, and
     * allows tracking of per-request meta data.
     *
     * @return the id of this request, or -1 if it has been removed from the query select statement
     */
    public int getRequestId() {
        return parent.getGrouping().indexOf(this);
    }

    /**
     * Returns the root {@link GroupingOperation} that defines this request. As long as this remains unset, the request
     * is void.
     *
     * @return the root operation.
     */
    public GroupingOperation getRootOperation() {
        return root;
    }

    /**
     * Sets the root {@link GroupingOperation} that defines this request. As long as this remains unset, the request is
     * void.
     *
     * @param root the root operation to set.
     * @return this, to allow chaining.
     */
    public GroupingRequest setRootOperation(GroupingOperation root) {
        this.root = root;
        return this;
    }

    /**
     * Returns the {@link TimeZone} used when resolving time expressions such as {@link
     * com.yahoo.search.grouping.request.DayOfMonthFunction} and {@link com.yahoo.search.grouping.request.HourOfDayFunction}.
     *
     * @return the time zone in use.
     */
    public TimeZone getTimeZone() {
        return timeZone;
    }

    /**
     * Sets the {@link TimeZone} used when resolving time expressions such as {@link
     * com.yahoo.search.grouping.request.DayOfMonthFunction} and {@link com.yahoo.search.grouping.request.HourOfDayFunction}.
     *
     * @param timeZone the time zone to set.
     * @return this, to allow chaining.
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
     * @param result the search result that contains the root group.
     * @return the result {@link RootGroup} of this request, or null if not found.
     */
    public RootGroup getResultGroup(Result result) {
        Hit root = result.hits().get(new URI(new RootId(getRequestId()).toString()), -1);
        if ( ! (root instanceof RootGroup)) {
            return null;
        }
        return (RootGroup)root;
    }

    /**
     * Returns the list of {@link Continuation}s of this request. This is used by the executing grouping searcher to
     * allow pagination of grouping results.
     *
     * @return the list of Continuations.
     */
    public List<Continuation> continuations() {
        return continuations;
    }

    @Beta
    public OptionalInt defaultMaxHits() {
        return defaultMaxHits != null ? OptionalInt.of(defaultMaxHits) : OptionalInt.empty();
    }

    @Beta public void setDefaultMaxHits(int v) { this.defaultMaxHits = v; }

    @Beta
    public OptionalInt defaultMaxGroups() {
        return defaultMaxGroups != null ? OptionalInt.of(defaultMaxGroups) : OptionalInt.empty();
    }

    @Beta public void setDefaultMaxGroups(int v) { this.defaultMaxGroups = v; }

    @Beta
    public OptionalLong globalMaxGroups() {
        return globalMaxGroups != null ? OptionalLong.of(globalMaxGroups) : OptionalLong.empty();
    }

    @Beta public void setGlobalMaxGroups(long v) { this.globalMaxGroups = v; }

    @Beta
    public OptionalDouble defaultPrecisionFactor() {
        return defaultPrecisionFactor != null ? OptionalDouble.of(defaultPrecisionFactor) : OptionalDouble.empty();
    }

    @Beta void setDefaultPrecisionFactor(double v) { this.defaultPrecisionFactor = v; }

    /**
     * Creates a new grouping request and adds it to the query.getSelect().getGrouping() list
     *
     * @param query the query to attach the request to.
     * @return The created request.
     */
    public static GroupingRequest newInstance(Query query) {
        GroupingRequest newRequest = new GroupingRequest(query.getSelect());
        query.getSelect().getGrouping().add(newRequest);
        return newRequest;
    }

    @Override
    public String toString() {
        return root == null ? "(empty)" : root.toString();
    }

}
