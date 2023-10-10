// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.http;

/**
 * Class containing the names of all benchmarking getHeaders in request and response
 *
 * @author Mathias Mølster Lidal
 */
public class BenchmarkingHeaders {

    public static final String REQUEST = "X-Yahoo-Vespa-Benchmarkdata";
    public static final String REQUEST_COVERAGE = "X-Yahoo-Vespa-Benchmarkdata-Coverage";

    public static final String NUM_HITS = "X-Yahoo-Vespa-NumHits";
    public static final String NUM_FASTHITS = "X-Yahoo-Vespa-NumFastHits";
    public static final String NUM_GROUPHITS = "X-Yahoo-Vespa-NumGroupHits";
    public static final String NUM_ERRORS = "X-Yahoo-Vespa-NumErrors";
    public static final String TOTAL_HIT_COUNT = "X-Yahoo-Vespa-TotalHitCount";
    public static final String NUM_DOCSUMS = "X-Yahoo-Vespa-NumDocsums";
    public static final String QUERY_HITS = "X-Yahoo-Vespa-QueryHits";
    public static final String QUERY_OFFSET = "X-Yahoo-Vespa-QueryOffset";
    public static final String SEARCH_TIME = "X-Yahoo-Vespa-SearchTime";
    public static final String ATTR_TIME = "X-Yahoo-Vespa-AttributeFetchTime";
    public static final String FILL_TIME = "X-Yahoo-Vespa-FillTime";
    public static final String DOCS_SEARCHED = "X-Yahoo-Vespa-DocsSearched";
    public static final String NODES_SEARCHED = "X-Yahoo-Vespa-NodesSearched";
    public static final String FULL_COVERAGE = "X-Yahoo-Vespa-FullCoverage";

}
