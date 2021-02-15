// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.query.parser.ParserFactory;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.yql.VespaGroupingStep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;


/**
 * The parameters defining the where-clause and groping of a query
 *
 * @author henrhoi
 */
public class Select implements Cloneable {

    /** The type representing the property arguments consumed by this */
    private static final QueryProfileType argumentType;
    private static final CompoundName argumentTypeName;

    public static final String SELECT = "select";
    public static final String WHERE = "where";
    public static final String GROUPING = "grouping";

    private final Query parent;
    private final List<GroupingRequest> groupingRequests;

    private String where;
    private String grouping;
    private String groupingExpressionString;

    static {
        argumentType = new QueryProfileType(SELECT);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(WHERE, "string"));
        argumentType.addField(new FieldDescription(GROUPING, "string"));
        argumentType.freeze();
        argumentTypeName = new CompoundName(argumentType.getId().getName());
    }

    public static QueryProfileType getArgumentType() { return argumentType; }

    /** Creates an empty select statement */
    public Select(Query query) {
        this("", "", query);
    }

    public Select(String where, String grouping, Query query) {
        this(where, grouping, null, query, Collections.emptyList());
    }

    private Select(String where, String grouping, String groupingExpressionString, Query query, List<GroupingRequest> groupingRequests) {
        this.where = Objects.requireNonNull(where, "A Select must have a where string (possibly the empty string)");
        this.grouping = Objects.requireNonNull(grouping, "A Select must have a select string (possibly the empty string)");
        this.groupingExpressionString = groupingExpressionString;
        this.parent = Objects.requireNonNull(query, "A Select must have a parent query");
        this.groupingRequests = deepCopy(groupingRequests, this);
    }

    private static List<GroupingRequest> deepCopy(List<GroupingRequest> groupingRequests, Select parentOfCopy) {
        List<GroupingRequest> copy = new ArrayList<>(groupingRequests.size());
        for (GroupingRequest request : groupingRequests)
            copy.add(request.copy(parentOfCopy));
        return copy;
    }

    /**
     * Sets the document selection criterion of the query.
     *
     * @param where the documents to select as a JSON string on the format specified in
     *        <a href="https://docs.vespa.ai/en/reference/select-reference.html">the select reference doc</a>
     */
    public void setWhereString(String where) {
        this.where = where;
        parent.getModel().setType(SELECT);

        // This replaces the current query
        parent.getModel().clearQueryTree();
    }

    /** Returns the where clause string previously assigned, or an empty string if none */
    public String getWhereString() { return where; }

    /**
     * Sets the grouping operation of the query.
     *
     * @param grouping the grouping to perform as a JSON string on the format specified in
     *        <a href="https://docs.vespa.ai/en/reference/select-reference.html">the select reference doc</a>
     */
    public void setGroupingString(String grouping) {
        groupingRequests.clear();
        this.grouping = grouping;
        SelectParser parser = (SelectParser) ParserFactory.newInstance(Query.Type.SELECT, new ParserEnvironment());
        for (VespaGroupingStep step : parser.getGroupingSteps(grouping)) {
            GroupingRequest.newInstance(parent)
                    .setRootOperation(step.getOperation())
                    .continuations().addAll(step.continuations());
        }
    }

    /**
     * Sets the grouping expression string directly.
     * This will not be parsed by this but will be accessed later by GroupingQueryParser.
     */
    public void setGroupingExpressionString(String groupingExpressionString) {
        this.groupingExpressionString = groupingExpressionString;
    }

    public String getGroupingExpressionString() { return groupingExpressionString; }

    /** Returns the grouping in the query */
    public String getGroupingString(){
        return grouping;
    }

    /**
     * Returns the query's {@link GroupingRequest} as a mutable list. Changing this directly changes the grouping
     * operations which will be performed by this query.
     */
    public List<GroupingRequest> getGrouping() { return groupingRequests; }

    @Override
    public String toString() {
        return "where: [" + where + "], grouping: [" + grouping + "]";
    }

    @Override
    public Object clone() {
        return new Select(where, grouping, groupingExpressionString, parent, groupingRequests);
    }

    public Select cloneFor(Query parent)  {
        return new Select(where, grouping, groupingExpressionString, parent, groupingRequests);
    }

}
