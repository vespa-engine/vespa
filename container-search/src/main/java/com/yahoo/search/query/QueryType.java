package com.yahoo.search.query;

import com.yahoo.search.Query;

/**
 * Detailed query type deciding how a query string is to be interpreted and processed.
 *
 * @author bratseth
 */
public class QueryType {

    private Query.Type type;

    private CompositeType compositeType;
    private Tokenization tokenization;
    private Syntax syntax;

    public QueryType(Query.Type type, CompositeType compositeType, Tokenization tokenization, Syntax syntax) {
        this.type = type;
        this.compositeType = compositeType;
        this.tokenization = tokenization;
        this.syntax = syntax;
    }

    /** Returns the overall type of this. */
    public Query.Type getType() { return type; }

    /** Returns the composite type terms should be collected as by default. */
    public CompositeType getCompositeType() { return compositeType; }

    /** Returns whether this should use internal tokenization, or delegate this to the linguistics component. */
    public Tokenization getTokenization() { return tokenization; }

    /** Returns the query syntax used in this query. */
    public Syntax getSyntax() { return syntax; }

    public enum CompositeType { and, or, weakAnd, phrase }
    public enum Tokenization { internal, linguistics }
    public enum Syntax { simple, web }

    public static QueryType from(Query.Type type) {
        return switch (type) {
            case ADVANCED ->     new QueryType(type, CompositeType.and, Tokenization.internal, Syntax.simple);
            case ALL ->          new QueryType(type, CompositeType.and, Tokenization.internal, Syntax.simple);
            case ANY ->          new QueryType(type, CompositeType.and, Tokenization.internal, Syntax.simple);
            case LINGUISTICS ->  new QueryType(type, CompositeType.and, Tokenization.internal, Syntax.simple);
            case PHRASE ->       new QueryType(type, CompositeType.and, Tokenization.internal, Syntax.simple);
            case PROGRAMMATIC -> new QueryType(type, CompositeType.and, Tokenization.internal, Syntax.simple);
            case SELECT ->       new QueryType(type, CompositeType.and, Tokenization.internal, Syntax.simple);
            case TOKENIZE ->     new QueryType(type, CompositeType.and, Tokenization.internal, Syntax.simple);
            case WEAKAND ->      new QueryType(type, CompositeType.and, Tokenization.internal, Syntax.simple);
            case WEB ->          new QueryType(type, CompositeType.and, Tokenization.internal, Syntax.simple);
            case YQL ->          new QueryType(type, CompositeType.and, Tokenization.internal, Syntax.simple);
        };
    }

}
