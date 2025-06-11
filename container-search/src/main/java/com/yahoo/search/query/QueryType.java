package com.yahoo.search.query;

import com.yahoo.search.Query;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Objects;

/**
 * Detailed query type deciding how a query string is to be interpreted and processed.
 *
 * @author bratseth
 */
public class QueryType {

    /** The type representing the property arguments consumed by this */
    private static final QueryProfileType argumentType;

    private final Query.Type type;

    private Composite composite;
    private Tokenization tokenization;
    private Syntax syntax;

    public static final String COMPOSITE = "composite";
    public static final String TOKENIZATION = "tokenization";
    public static final String SYNTAX = "syntax";

    static {
        argumentType = new QueryProfileType(Model.TYPE);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription("", "string")); // The Query.Type
        argumentType.addField(new FieldDescription(COMPOSITE, "string"));
        argumentType.addField(new FieldDescription(TOKENIZATION, "string"));
        argumentType.addField(new FieldDescription(SYNTAX, "string"));
        argumentType.freeze();
    }

    public static QueryProfileType getArgumentType() { return argumentType; }

    public QueryType(Query.Type type, Composite composite, Tokenization tokenization, Syntax syntax) {
        this.type = type;
        this.composite = composite;
        this.tokenization = tokenization;
        this.syntax = syntax;
    }

    /** Returns the overall type of this. */
    public Query.Type getType() { return type; }

    /** Returns the composite type terms should be collected as by default. */
    public Composite getComposite() { return composite; }

    /**
     * Sets the composite type terms should be collected as by default.
     *
     * @return this for chaining
     */
    public QueryType setComposite(Composite composite) {
        this.composite = composite;
        return this;
    }

    /** Sets the composite value from a string enum value. If the argument is null this does nothing. */
    public QueryType setComposite(String composite) {
        if (composite == null) return this;
        this.composite = Composite.valueOf(composite);
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

    /** Sets tokenization from a string enum value. If the argument is null this does nothing. */
    public QueryType setTokenization(String tokenization) {
        if (tokenization == null) return this;
        this.tokenization = Tokenization.valueOf(tokenization);
        return this;
    }

    /** Returns the query syntax used in this query. */
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

    /** Sets the syntax from a string enum value. If the argument is null this does nothing. */
    public QueryType setSyntax(String syntax) {
        if (syntax == null) return this;
        this.syntax = Syntax.valueOf(syntax);
        return this;
    }

    /** Throws IllegalArgumentException if the combination of options set in this are ot supported. */
    public void validate() {
        if (tokenization == Tokenization.linguistics && syntax != Syntax.none)
            throw new IllegalArgumentException(this + " is invalid: Linguistics tokenization can only be combined with syntax none");
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof QueryType other)) return false;
        if (other.type != this.type) return false;
        if (other.composite != this.composite) return false;
        if (other.tokenization != this.tokenization) return false;
        if (other.syntax != this.syntax) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, composite, tokenization, syntax);
    }

    @Override
    public String toString() {
        return "query type " + type +
               " [composite: " + composite + ", tokenization: " + tokenization + ", syntax: " + syntax + "]";
    }

    public enum Composite { and, or, phrase, weakAnd }

    public enum Tokenization { internal, linguistics }

    public enum Syntax { advanced, json, none, programmatic, simple, web, yql }

    public static QueryType from(Query.Type type) {
        return switch (type) {
            case ADVANCED ->     new QueryType(type, Composite.and,     Tokenization.internal,    Syntax.advanced);
            case ALL ->          new QueryType(type, Composite.and,     Tokenization.internal,    Syntax.simple);
            case ANY ->          new QueryType(type, Composite.or,      Tokenization.internal,    Syntax.simple);
            case LINGUISTICS ->  new QueryType(type, Composite.weakAnd, Tokenization.linguistics, Syntax.none);
            case PHRASE ->       new QueryType(type, Composite.phrase,  Tokenization.internal,    Syntax.none);
            case PROGRAMMATIC -> new QueryType(type, Composite.and,     Tokenization.internal,    Syntax.programmatic);
            case SELECT ->       new QueryType(type, Composite.and,     Tokenization.internal,    Syntax.json);
            case TOKENIZE ->     new QueryType(type, Composite.weakAnd, Tokenization.internal,    Syntax.none);
            case WEAKAND ->      new QueryType(type, Composite.weakAnd, Tokenization.internal,    Syntax.simple);
            case WEB ->          new QueryType(type, Composite.and,     Tokenization.internal,    Syntax.web);
            case YQL ->          new QueryType(type, Composite.and,     Tokenization.internal,    Syntax.yql);
        };
    }

    public static QueryType from(String typeName) {
        return QueryType.from(Query.Type.getType(typeName));
    }

}
