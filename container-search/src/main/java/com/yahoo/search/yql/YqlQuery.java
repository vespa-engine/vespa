// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

/**
 * A Yql query. These usually contains variables, which allows the yql query to be parsed once at configuration
 * time and turned into fully specified queries at request time without reparsing.
 *
 * @author bratseth
 */
// TODO: This is just a skeleton
public class YqlQuery {

    private YqlQuery(String yqlQuery) {
        // TODO
    }

    /** Creates a YQl query form a string */
    public static YqlQuery from(String yqlQueryString) {
        return new YqlQuery(yqlQueryString);
    }

}
