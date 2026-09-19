// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.api.annotations.Beta;
import com.yahoo.language.Language;
import com.yahoo.search.Query;
import com.yahoo.search.query.QueryType;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.Parser;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.query.parser.ParserFactory;
import com.yahoo.search.searchchain.Execution;

import java.util.Set;

/**
 * Helper class for creating query subtrees that can be created by a single pseudo-item in YQL,
 * such as text().
 *
 * @author bratseth
 */
@Beta
public class Items {

    /**
     * Creates a query tree representing a text() item, where the context is taken from the given query and execution.
     *
     * @param fieldOrFieldSet the name of the field or fieldSet to be searched
     * @param text the (unprocessed) user text to turns into an item tree
     * @param query the query this is in context of
     * @param execution the execution this in context of
     */
    public static Item text(String fieldOrFieldSet,
                            String text,
                            Query query,
                            Execution execution) {
        return text(fieldOrFieldSet,
                    text,
                    query.getModel().getQueryType(),
                    query.getModel().getParsingLanguage(),
                    query.getModel().getSources(),
                    query.getModel().getRestrict(),
                    ParserEnvironment.fromExecutionContext(execution.context()));
    }

    /**
     * Creates a query tree representing a text() item.
     *
     * @param fieldOrFieldSet the name of the field or fieldSet to be searched
     * @param text the (unprocessed) user text to turns into an item tree
     * @param language the language that should be assigned to the items
     * @param queryType the query type settings to use
     * @param sources the sources to search, or empty to search all
     * @param restrict the schemas to restrict to within those sources, or empty if no restrictions
     * @param environment the parser environment to use
     */
    public static Item text(String fieldOrFieldSet,
                            String text,
                            QueryType queryType,
                            Language language,
                            Set<String> sources,
                            Set<String> restrict,
                            ParserEnvironment environment) {
        Parser parser = ParserFactory.newInstance(queryType, environment);
        return parser.parse(new Parsable().setQuery(text)
                                          .addSources(sources)
                                          .addRestricts(restrict)
                                          .setLanguage(language)
                                          .setDefaultIndexName(fieldOrFieldSet)).getRoot();
    }

    public static Item createText(String fieldOrFieldSet,
                                  String text,
                                  Query query,
                                  Execution execution) {
        Parser parser = ParserFactory.newInstance(query.getModel().getQueryType(),
                                                  ParserEnvironment.fromExecutionContext(execution.context()));
        return parser.parse(new Parsable().setQuery(text)
                                          .addSources(query.getModel().getSources())
                                          .addRestricts(query.getModel().getRestrict())
                                          .setLanguage(query.getModel().getParsingLanguage())
                                          .setDefaultIndexName(fieldOrFieldSet)).getRoot();
    }


}
