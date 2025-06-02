package com.yahoo.search.query;

import com.yahoo.search.Query;

import java.util.Objects;

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

    /**
     * Returns the overall type of this.
     */
    public Query.Type getType() {return type;}

    /**
     * Returns the composite type terms should be collected as by default.
     */
    public CompositeType getCompositeType() {return compositeType;}

    /**
     * Sets the composite type terms should be collected as by default.
     *
     * @return this for chaining
     */
    public QueryType setCompositeType(CompositeType compositeType) {
        this.compositeType = compositeType;
        return this;
    }

    /**
     * Returns whether this should use internal tokenization, or delegate this to the linguistics component.
     */
    public Tokenization getTokenization() {return tokenization;}

    /**
     * Sets whether this should use internal tokenization, or delegate this to the linguistics component.
     *
     * @return this for chaining
     */
    public QueryType setTokenization(Tokenization tokenization) {
        this.tokenization = tokenization;
        return this;
    }

    /**
     * Returns the query syntax used in this query.
     */
    public Syntax getSyntax() {return syntax;}

    /**
     * Sets the query syntax used in this query.
     *
     * @return this for chaining
     */
    public QueryType setSyntax(Syntax syntax) {
        this.syntax = syntax;
        return this;
    }

    /**
     * Throws IllegalArgumentException if the combination of options set in this are ot supported.
     */
    public void validate() {
        if (tokenization == Tokenization.linguistics && syntax != Syntax.none)
            throw new IllegalArgumentException(this + " is invalid: Linguistics tokenization can only be combined with syntax none");
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof QueryType other)) return false;
        if (other.type != this.type) return false;
        if (other.compositeType != this.compositeType) return false;
        if (other.tokenization != this.tokenization) return false;
        if (other.syntax != this.syntax) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, compositeType, tokenization, syntax);
    }

    @Override
    public String toString() {
        return "query type " + type +
               " [compositeType: " + compositeType + ", toenization: " + tokenization + ", syntax: " + syntax + "]";
    }

    public enum CompositeType { and, or, phrase, weakAnd }

    public enum Tokenization { internal, linguistics }

    public enum Syntax { advanced, json, none, programmatic, simple, web, yql }

    public static QueryType from(Query.Type type) {
        return switch (type) {
            case ADVANCED ->     new QueryType(type, CompositeType.and,     Tokenization.internal,    Syntax.advanced);
            case ALL ->          new QueryType(type, CompositeType.and,     Tokenization.internal,    Syntax.simple);
            case ANY ->          new QueryType(type, CompositeType.or,      Tokenization.internal,    Syntax.simple);
            case LINGUISTICS ->  new QueryType(type, CompositeType.weakAnd, Tokenization.linguistics, Syntax.none);
            case PHRASE ->       new QueryType(type, CompositeType.phrase,  Tokenization.internal,    Syntax.simple);
            case PROGRAMMATIC -> new QueryType(type, CompositeType.and,     Tokenization.internal,    Syntax.programmatic);
            case SELECT ->       new QueryType(type, CompositeType.and,     Tokenization.internal,    Syntax.json);
            case TOKENIZE ->     new QueryType(type, CompositeType.weakAnd, Tokenization.internal,    Syntax.none);
            case WEAKAND ->      new QueryType(type, CompositeType.weakAnd, Tokenization.internal,    Syntax.simple);
            case WEB ->          new QueryType(type, CompositeType.and,     Tokenization.internal,    Syntax.web);
            case YQL ->          new QueryType(type, CompositeType.and,     Tokenization.internal,    Syntax.yql);
        };
    }

    public static QueryType from(String typeName) {
        return QueryType.from(Query.Type.getType(typeName));
    }

}
