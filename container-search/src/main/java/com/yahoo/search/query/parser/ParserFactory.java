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
        type.validate();
        environment.setType(type);
        return switch (type.getType()) {
            case ALL -> new AllParser(environment);
            case ANY -> new AnyParser(environment);
            case PHRASE -> new PhraseParser(environment);
            case ADVANCED -> new AdvancedParser(environment);
            case WEB -> new WebParser(environment);
            case PROGRAMMATIC -> new ProgrammaticParser();
            case YQL -> new YqlParser(environment);
            case SELECT -> new SelectParser(environment);
            case WEAKAND -> new AllParser(environment);
            case TOKENIZE -> new TokenizeParser(environment);
            case LINGUISTICS -> new LinguisticsParser(environment);
        };
    }

}
