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
import java.util.List;


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

    private static Model model;
    private Query parent;
    private String where = "";
    private String grouping = "";
    private List<GroupingRequest> groupingRequests = new ArrayList<>();

    static {
        argumentType = new QueryProfileType(SELECT);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(WHERE, "string", "where"));
        argumentType.addField(new FieldDescription(GROUPING, "string", "grouping"));
        argumentType.freeze();
        argumentTypeName=new CompoundName(argumentType.getId().getName());
    }

    public static QueryProfileType getArgumentType() { return argumentType; }

    public Select(String where, String grouping){
        this.where = where;
        this.grouping = grouping;
    }

    public Select(Query query) {
        setParent(query);
        model = query.getModel();
    }


    /** Returns the query owning this, never null */
    private Query getParent() { return parent; }


    /** Assigns the query owning this */
    public void setParent(Query parent) {
        if (parent==null) throw new NullPointerException("A query models owner cannot be null");
        this.parent = parent;
    }


    /** Set the where-clause for the query. Must be a JSON-string, with the format described in the Select Reference doc:
     * @see <a href="https://docs.vespa.ai/documentation/reference/select-reference.html">https://docs.vespa.ai/documentation/reference/select-reference.html</a>
     */
    public void setWhereString(String where) {
        this.where = where;
        model.setType(SELECT);

        // Setting the queryTree to null
        model.setQueryString(null);
    }


    /** Returns the where-clause in the query */
    public String getWhereString(){ return where; }


    /** Set the grouping-string for the query. Must be a JSON-string, with the format described in the Select Reference doc:
     * @see <a href="https://docs.vespa.ai/documentation/reference/select-reference.html">https://docs.vespa.ai/documentation/reference/select-reference.html</a>
     * */
    public void setGroupingString(String grouping){
        this.grouping = grouping;
        SelectParser parser = (SelectParser) ParserFactory.newInstance(Query.Type.SELECT, new ParserEnvironment());

        for (VespaGroupingStep step : parser.getGroupingSteps(grouping)) {
            GroupingRequest.newInstance(parent)
                    .setRootOperation(step.getOperation())
                    .continuations().addAll(step.continuations());
        }
    }


    /** Returns the grouping in the query */
    public String getGroupingString(){
        return grouping;
    }


    /** Returns the query's {@link GroupingRequest} objects, as mutable list */
    public List<GroupingRequest> getGrouping(){ return groupingRequests; }


    @Override
    public String toString() {
        return "where: [" + where + "], grouping: [" + grouping+ "]";
    }

}
