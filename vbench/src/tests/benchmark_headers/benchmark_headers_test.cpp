// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST("require that benchmark headers can be set") {
    vbench::BenchmarkHeaders headers;
    EXPECT_FALSE(headers.num_hits.is_set);
    EXPECT_FALSE(headers.num_fasthits.is_set);
    EXPECT_FALSE(headers.num_grouphits.is_set);
    EXPECT_FALSE(headers.num_errors.is_set);
    EXPECT_FALSE(headers.total_hit_count.is_set);
    EXPECT_FALSE(headers.num_docsums.is_set);
    EXPECT_FALSE(headers.query_hits.is_set);
    EXPECT_FALSE(headers.query_offset.is_set);
    EXPECT_FALSE(headers.search_time.is_set);
    EXPECT_FALSE(headers.attr_time.is_set);
    EXPECT_FALSE(headers.fill_time.is_set);
    EXPECT_FALSE(headers.docs_searched.is_set);
    EXPECT_FALSE(headers.nodes_searched.is_set);
    EXPECT_FALSE(headers.full_coverage.is_set);

    headers.handleHeader("X-Yahoo-Vespa-NumHits", "1");
    headers.handleHeader("X-Yahoo-Vespa-NumFastHits", "2");
    headers.handleHeader("X-Yahoo-Vespa-NumGroupHits", "3");
    headers.handleHeader("X-Yahoo-Vespa-NumErrors", "4");
    headers.handleHeader("X-Yahoo-Vespa-TotalHitCount", "5");
    headers.handleHeader("X-Yahoo-Vespa-NumDocsums", "6");
    headers.handleHeader("X-Yahoo-Vespa-QueryHits", "7");
    headers.handleHeader("X-Yahoo-Vespa-QueryOffset", "8");
    headers.handleHeader("X-Yahoo-Vespa-SearchTime", "9");
    headers.handleHeader("X-Yahoo-Vespa-AttributeFetchTime" , "10");
    headers.handleHeader("X-Yahoo-Vespa-FillTime", "11");
    headers.handleHeader("X-Yahoo-Vespa-DocsSearched", "12");
    headers.handleHeader("X-Yahoo-Vespa-NodesSearched", "13");
    headers.handleHeader("X-Yahoo-Vespa-FullCoverage", "14");

    EXPECT_TRUE(headers.num_hits.is_set);
    EXPECT_TRUE(headers.num_fasthits.is_set);
    EXPECT_TRUE(headers.num_grouphits.is_set);
    EXPECT_TRUE(headers.num_errors.is_set);
    EXPECT_TRUE(headers.total_hit_count.is_set);
    EXPECT_TRUE(headers.num_docsums.is_set);
    EXPECT_TRUE(headers.query_hits.is_set);
    EXPECT_TRUE(headers.query_offset.is_set);
    EXPECT_TRUE(headers.search_time.is_set);
    EXPECT_TRUE(headers.attr_time.is_set);
    EXPECT_TRUE(headers.fill_time.is_set);
    EXPECT_TRUE(headers.docs_searched.is_set);
    EXPECT_TRUE(headers.nodes_searched.is_set);
    EXPECT_TRUE(headers.full_coverage.is_set);

    EXPECT_EQUAL(headers.num_hits.value, 1.0);
    EXPECT_EQUAL(headers.num_fasthits.value, 2.0);
    EXPECT_EQUAL(headers.num_grouphits.value, 3.0);
    EXPECT_EQUAL(headers.num_errors.value, 4.0);
    EXPECT_EQUAL(headers.total_hit_count.value, 5.0);
    EXPECT_EQUAL(headers.num_docsums.value, 6.0);
    EXPECT_EQUAL(headers.query_hits.value, 7.0);
    EXPECT_EQUAL(headers.query_offset.value, 8.0);
    EXPECT_EQUAL(headers.search_time.value, 9.0);
    EXPECT_EQUAL(headers.attr_time.value, 10.0);
    EXPECT_EQUAL(headers.fill_time.value, 11.0);
    EXPECT_EQUAL(headers.docs_searched.value, 12.0);
    EXPECT_EQUAL(headers.nodes_searched.value, 13.0);
    EXPECT_EQUAL(headers.full_coverage.value, 14.0);
}

TEST("require that benchmark headers can be converted to string") {
    vbench::BenchmarkHeaders headers;
    headers.handleHeader("X-Yahoo-Vespa-NumErrors", "4");
    headers.handleHeader("X-Yahoo-Vespa-TotalHitCount", "5");
    headers.handleHeader("X-Yahoo-Vespa-NumDocsums", "6");
    vbench::string result = headers.toString();
    fprintf(stderr, "%s", result.c_str());
}

TEST_MAIN() { TEST_RUN_ALL(); }
