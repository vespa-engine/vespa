// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.parser;

import com.yahoo.search.query.QueryTree;

/**
 * Defines the interface of a query parser. To construct an instance of this class, use the {@link ParserFactory}.
 *
 * @author Simon Thoresen Hult
 */
public interface Parser {

    /**
     * Parser the given {@link Parsable}, and returns a corresponding
     * {@link QueryTree}. If parsing fails without an exception, the contained
     * root will be an instance of {@link com.yahoo.prelude.query.NullItem}.
     *
     * @param query
     *            the Parsable to parse
     * @return the parsed QueryTree, never null
     */
    QueryTree parse(Parsable query);

}
