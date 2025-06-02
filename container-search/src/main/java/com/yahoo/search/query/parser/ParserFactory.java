// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.parser;

import com.yahoo.prelude.query.parser.*;
import com.yahoo.search.Query;
import com.yahoo.search.query.QueryType;
import com.yahoo.search.query.SelectParser;
import com.yahoo.search.yql.YqlParser;

/**
 * Implements a factory for {@link Parser}.
 *
 * @author Simon Thoresen Hult
 */
public final class ParserFactory {

    private ParserFactory() {
        // hide
    }

    public static Parser newInstance(Query.Type type, ParserEnvironment environment) {
        return newInstance(QueryType.from(type), environment);
    }

    /**
     * Creates a {@link Parser} appropriate for the given <code>Query.Type</code>, providing the Parser with access to
     * the {@link ParserEnvironment} given.
     *
     * @param type        the query type for which to create a Parser
     * @param environment the environment settings to attach to the Parser
     * @return the created Parser
     */
    @SuppressWarnings("deprecation")
    public static Parser newInstance(QueryType type, ParserEnvironment environment) {
        // This isn't a clean switch from type.getSyntax for legacy reasons.
        // With some more effort the various parsers for the same syntax could be merged into one
        // since the variance is covered in environment.getType which is accessible to the parsers.
        type.validate();
        environment.setType(type);
        if (type.getSyntax() == QueryType.Syntax.advanced)
            return new AdvancedParser(environment);
        if (type.getSyntax() == QueryType.Syntax.json)
            return new SelectParser(environment);
        if (type.getSyntax() == QueryType.Syntax.programmatic)
            return new ProgrammaticParser();
        if (type.getSyntax() == QueryType.Syntax.web)
            return new WebParser(environment);
        if (type.getSyntax() == QueryType.Syntax.yql)
            return new YqlParser(environment);
        if (type.getSyntax() == QueryType.Syntax.none) {
            if (type.getTokenization() == QueryType.Tokenization.linguistics)
                return new LinguisticsParser(environment);
            else
                return new TokenizeParser(environment);
        }
        else if (type.getSyntax() == QueryType.Syntax.simple) {
            if (type.getCompositeType() == QueryType.CompositeType.or)
                return new AnyParser(environment);
            if (type.getCompositeType() == QueryType.CompositeType.phrase)
                return new PhraseParser(environment);
            else
                return new AllParser(environment); // Covers both 'weakAnd' and 'and'
        }
        throw new IllegalStateException("Unsupported query syntax '" + type.getSyntax() + "'");
    }

}
