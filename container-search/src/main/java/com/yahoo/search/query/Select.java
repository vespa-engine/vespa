// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;


import com.yahoo.document.select.rule.AttributeNode;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.query.parser.ParserFactory;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.yql.YqlParser;

import static com.yahoo.fs4.PacketDumper.PacketType.query;


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

    private String where = "";
    private String grouping = "";


    /** returns the query owning this, never null */
    public Query getParent() { return parent; }

    /** Assigns the query owning this */
    public void setParent(Query parent) {
        if (parent==null) throw new NullPointerException("A query models owner cannot be null");
        this.parent = parent;
    }

    public static Select getFrom(Query q) {
        return (Select) q.properties().get(argumentTypeName);
    }


    public void setQueryString(String queryString) {
        model.setQueryString(queryString);
    }

    /** Set the where-clause for the query. Must be a JSON-string. */
    public void setWhere(String where){
        this.where = where;
        //QueryTree newTree = getParsedQueryTree();

        //if (newTree != null){
        //    parent.getModel().getQueryTree().setRoot(newTree.getRoot());
        //}
    }

    public QueryTree getParsedQueryTree(){
        SelectParser parser = (SelectParser) ParserFactory.newInstance(Query.Type.SELECT, new ParserEnvironment());
        QueryTree newTree = null;
        try {
            System.out.println(parent.properties().getString(Select.WHERE));
            QueryTree ewTree = parser.parse(Parsable.fromQueryModel(parent.getModel()).setQuery(parent.properties().getString(Select.WHERE)));
            System.out.println(ewTree.toString());

        } catch (RuntimeException e) {
            throw new RuntimeException("Could not instantiate query from YQL", e);
        }
        return newTree;
    }









    /** Returns the where-clause in the query */
    public String getWhere(){
        return this.where;
    }

    /** Set the grouping-string for the query. Must be a JSON-string. */
    public void setGrouping(String grouping){
        this.grouping = grouping;
    }

    /** Returns the grouping in the query */
    public String getGrouping(){
        return this.grouping;
    }

    @Override
    public String toString() {
        return "where: [" + where + "], grouping: [" + grouping+ "]";
    }

}
