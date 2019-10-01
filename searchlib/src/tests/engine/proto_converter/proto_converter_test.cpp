// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchlib/engine/proto_converter.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/slime/binary_format.h>

#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Winline"

using Converter = ::search::engine::ProtoConverter;

using SearchRequest = ::search::engine::SearchRequest;
using SearchReply = ::search::engine::SearchReply;

using DocsumRequest = ::search::engine::DocsumRequest;
using DocsumReply = ::search::engine::DocsumReply;

using MonitorRequest = ::search::engine::MonitorRequest;
using MonitorReply = ::search::engine::MonitorReply;

using vespalib::Slime;
using vespalib::Memory;
using vespalib::slime::BinaryFormat;

//-----------------------------------------------------------------------------

struct SearchRequestTest : ::testing::Test {
    Converter::ProtoSearchRequest proto;
    SearchRequest request;
    void convert() { Converter::search_request_from_proto(proto, request); }
};

TEST_F(SearchRequestTest, require_that_offset_is_converted) {
    proto.set_offset(123);
    convert();
    EXPECT_EQ(request.offset, 123);
}

TEST_F(SearchRequestTest, require_that_hits_is_converted) {
    proto.set_hits(17);
    convert();
    EXPECT_EQ(request.maxhits, 17);
}

TEST_F(SearchRequestTest, require_that_timeout_is_converted) {
    proto.set_timeout(500);
    convert();
    EXPECT_EQ(request.getTimeout().ms(), 500);
}

TEST_F(SearchRequestTest, require_that_trace_level_is_converted) {
    proto.set_trace_level(9);
    convert();
    EXPECT_EQ(request.getTraceLevel(), 9);
}

TEST_F(SearchRequestTest, require_that_sorting_is_converted) {
    auto *sort_field = proto.add_sorting();
    sort_field->set_ascending(true);
    sort_field->set_field("foo");
    sort_field = proto.add_sorting();
    sort_field->set_ascending(false);
    sort_field->set_field("bar");
    convert();
    EXPECT_EQ(request.sortSpec, "+foo -bar");
}

TEST_F(SearchRequestTest, require_that_session_key_is_converted) {
    proto.set_session_key("my-session");
    convert();
    EXPECT_EQ(std::string(&request.sessionId[0], request.sessionId.size()), "my-session");
}

TEST_F(SearchRequestTest, require_that_document_type_is_converted) {
    proto.set_document_type("music");
    convert();
    EXPECT_EQ(request.propertiesMap.matchProperties().lookup("documentdb", "searchdoctype").get(""), "music");
}

TEST_F(SearchRequestTest, require_that_cache_grouping_is_converted) {
    proto.set_cache_grouping(true);
    convert();
    EXPECT_TRUE(request.propertiesMap.cacheProperties().lookup("grouping").found());
    EXPECT_FALSE(request.propertiesMap.cacheProperties().lookup("query").found());
}

TEST_F(SearchRequestTest, require_that_cache_query_is_converted) {
    proto.set_cache_query(true);
    convert();
    EXPECT_FALSE(request.propertiesMap.cacheProperties().lookup("grouping").found());
    EXPECT_TRUE(request.propertiesMap.cacheProperties().lookup("query").found());
}

TEST_F(SearchRequestTest, require_that_rank_profile_is_converted) {
    proto.set_rank_profile("mlr");
    convert();
    EXPECT_EQ(request.ranking, "mlr");
}

TEST_F(SearchRequestTest, require_that_feature_overrides_are_converted) {
    auto *prop = proto.add_feature_overrides();
    prop->set_name("foo");
    prop->add_values("a");
    prop = proto.add_feature_overrides();
    prop->set_name("bar");
    prop->add_values("b");
    prop->add_values("c");
    auto *tprop = proto.add_tensor_feature_overrides();
    tprop->set_name("x1");
    tprop->set_value("[1,2,3]");
    tprop = proto.add_tensor_feature_overrides();
    tprop->set_name("y1");
    tprop->set_value("[4,5]");
    convert();
    auto foo = request.propertiesMap.featureOverrides().lookup("foo");
    EXPECT_EQ(foo.size(), 1u);
    EXPECT_EQ(foo.get(), "a");
    auto bar = request.propertiesMap.featureOverrides().lookup("bar");
    EXPECT_EQ(bar.size(), 2u);
    EXPECT_EQ(bar.get(), "b");
    EXPECT_EQ(bar.getAt(1), "c");
    auto x1 = request.propertiesMap.featureOverrides().lookup("x1");
    EXPECT_EQ(x1.size(), 1u);
    EXPECT_EQ(x1.get(), "[1,2,3]");
    auto y1 = request.propertiesMap.featureOverrides().lookup("y1");
    EXPECT_EQ(y1.size(), 1u);
    EXPECT_EQ(y1.get(), "[4,5]");
}

TEST_F(SearchRequestTest, require_that_rank_properties_are_converted) {
    auto *prop = proto.add_rank_properties();
    prop->set_name("foo");
    prop->add_values("a");
    prop = proto.add_rank_properties();
    prop->set_name("bar");
    prop->add_values("b");
    prop->add_values("c");
    auto *tprop = proto.add_tensor_rank_properties();
    tprop->set_name("x1");
    tprop->set_value("[1,2,3]");
    tprop = proto.add_tensor_rank_properties();
    tprop->set_name("y1");
    tprop->set_value("[4,5]");
    convert();
    auto foo = request.propertiesMap.rankProperties().lookup("foo");
    EXPECT_EQ(foo.size(), 1u);
    EXPECT_EQ(foo.get(), "a");
    auto bar = request.propertiesMap.rankProperties().lookup("bar");
    EXPECT_EQ(bar.size(), 2u);
    EXPECT_EQ(bar.get(), "b");
    EXPECT_EQ(bar.getAt(1), "c");
    auto x1 = request.propertiesMap.rankProperties().lookup("x1");
    EXPECT_EQ(x1.size(), 1u);
    EXPECT_EQ(x1.get(), "[1,2,3]");
    auto y1 = request.propertiesMap.rankProperties().lookup("y1");
    EXPECT_EQ(y1.size(), 1u);
    EXPECT_EQ(y1.get(), "[4,5]");
}

TEST_F(SearchRequestTest, require_that_grouping_blob_is_converted) {
    proto.set_grouping_blob("grouping-blob");
    convert();
    EXPECT_EQ(std::string(&request.groupSpec[0], request.groupSpec.size()), "grouping-blob");
}

TEST_F(SearchRequestTest, require_that_geo_location_is_converted) {
    proto.set_geo_location("x,y");
    convert();
    EXPECT_EQ(request.location, "x,y");
}

TEST_F(SearchRequestTest, require_that_query_tree_blob_is_converted) {
    proto.set_query_tree_blob("query-tree-blob");
    convert();
    EXPECT_EQ(std::string(&request.stackDump[0], request.stackDump.size()), "query-tree-blob");
}

//-----------------------------------------------------------------------------

struct SearchReplyTest : ::testing::Test {
    SearchReply reply;
    Converter::ProtoSearchReply proto;
    void convert() { Converter::search_reply_to_proto(reply, proto); }
};

TEST_F(SearchReplyTest, require_that_total_hit_count_is_converted) {
    reply.totalHitCount = 9001;
    convert();
    EXPECT_EQ(proto.total_hit_count(), 9001);
}

TEST_F(SearchReplyTest, require_that_coverage_docs_is_converted) {
    reply.coverage.setCovered(150000);
    convert();
    EXPECT_EQ(proto.coverage_docs(), 150000);
}

TEST_F(SearchReplyTest, require_that_active_docs_is_converted) {
    reply.coverage.setActive(200000);
    convert();
    EXPECT_EQ(proto.active_docs(), 200000);
}

TEST_F(SearchReplyTest, require_that_soon_active_docs_is_converted) {
    reply.coverage.setSoonActive(250000);
    convert();
    EXPECT_EQ(proto.soon_active_docs(), 250000);
}

TEST_F(SearchReplyTest, require_that_degraded_by_match_phase_is_converted) {
    reply.coverage.degradeMatchPhase();
    convert();
    EXPECT_TRUE(proto.degraded_by_match_phase());
    EXPECT_FALSE(proto.degraded_by_soft_timeout());
}

TEST_F(SearchReplyTest, require_that_degraded_by_soft_timeout_is_converted) {
    reply.coverage.degradeTimeout();
    convert();
    EXPECT_FALSE(proto.degraded_by_match_phase());
    EXPECT_TRUE(proto.degraded_by_soft_timeout());
}

TEST_F(SearchReplyTest, require_that_multiple_degraded_reasons_are_converted) {
    reply.coverage.degradeMatchPhase();
    reply.coverage.degradeTimeout();
    convert();
    EXPECT_TRUE(proto.degraded_by_match_phase());
    EXPECT_TRUE(proto.degraded_by_soft_timeout());
}

TEST_F(SearchReplyTest, require_that_hits_are_converted) {
    constexpr size_t len = document::GlobalId::LENGTH;
    ASSERT_EQ(len, 12);
    char id0[len] = { 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12};
    char id1[len] = {11,12,13,14,15,16,17,18,19,20,21,22};
    char id2[len] = {21,22,23,24,25,26,27,28,29,30,31,32};
    reply.hits.resize(3);
    reply.hits[0].gid = document::GlobalId(id0);
    reply.hits[0].metric = 100.0;
    reply.hits[1].gid = document::GlobalId(id1);
    reply.hits[1].metric = 50.0;
    reply.hits[2].gid = document::GlobalId(id2);
    reply.hits[2].metric = 10.0;
    convert();
    ASSERT_EQ(proto.hits_size(), 3);
    EXPECT_EQ(proto.hits(0).global_id(), std::string(id0, len));
    EXPECT_EQ(proto.hits(0).relevance(), 100.0);
    EXPECT_TRUE(proto.hits(0).sort_data().empty());
    EXPECT_EQ(proto.hits(1).global_id(), std::string(id1, len));
    EXPECT_EQ(proto.hits(1).relevance(), 50.0);
    EXPECT_TRUE(proto.hits(1).sort_data().empty());
    EXPECT_EQ(proto.hits(2).global_id(), std::string(id2, len));
    EXPECT_EQ(proto.hits(2).relevance(), 10.0);
    EXPECT_TRUE(proto.hits(2).sort_data().empty());
}

TEST_F(SearchReplyTest, require_that_hits_with_sort_data_are_converted) {
    constexpr size_t len = document::GlobalId::LENGTH;
    ASSERT_EQ(len, 12);
    char id0[len] = { 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12};
    char id1[len] = {11,12,13,14,15,16,17,18,19,20,21,22};
    char id2[len] = {21,22,23,24,25,26,27,28,29,30,31,32};
    reply.hits.resize(3);
    reply.hits[0].gid = document::GlobalId(id0);
    reply.hits[0].metric = 100.0;
    reply.hits[1].gid = document::GlobalId(id1);
    reply.hits[1].metric = 50.0;
    reply.hits[2].gid = document::GlobalId(id2);
    reply.hits[2].metric = 10.0;
    vespalib::string sort_data("fooxybar");
    reply.sortData.assign(sort_data.begin(), sort_data.end());
    reply.sortIndex.push_back(0);
    reply.sortIndex.push_back(3); // hit1: 'foo'
    reply.sortIndex.push_back(5); // hit2: 'xy'
    reply.sortIndex.push_back(8); // hit3: 'bar'
    convert();
    ASSERT_EQ(proto.hits_size(), 3);
    EXPECT_EQ(proto.hits(0).global_id(), std::string(id0, len));
    EXPECT_EQ(proto.hits(0).relevance(), 100.0);
    EXPECT_EQ(proto.hits(0).sort_data(), "foo");
    EXPECT_EQ(proto.hits(1).global_id(), std::string(id1, len));
    EXPECT_EQ(proto.hits(1).relevance(), 50.0);
    EXPECT_EQ(proto.hits(1).sort_data(), "xy");
    EXPECT_EQ(proto.hits(2).global_id(), std::string(id2, len));
    EXPECT_EQ(proto.hits(2).relevance(), 10.0);
    EXPECT_EQ(proto.hits(2).sort_data(), "bar");
}

TEST_F(SearchReplyTest, require_that_grouping_blob_is_converted) {
    vespalib::string tmp("grouping-result");
    reply.groupResult.assign(tmp.begin(), tmp.end());
    convert();
    EXPECT_EQ(proto.grouping_blob(), "grouping-result");
}

TEST_F(SearchReplyTest, require_that_slime_trace_is_converted) {
    reply.propertiesMap.lookupCreate("trace").add("slime", "slime-trace");
    convert();
    EXPECT_EQ(proto.slime_trace(), "slime-trace");
}

//-----------------------------------------------------------------------------

struct DocsumRequestTest : ::testing::Test {
    Converter::ProtoDocsumRequest proto;
    DocsumRequest request;
    DocsumRequestTest() : proto(), request(true) {} // <- use root slime
    void convert() { Converter::docsum_request_from_proto(proto, request); }
};

TEST_F(DocsumRequestTest, require_that_root_slime_is_used) {
    EXPECT_TRUE(request.useRootSlime());
}

TEST_F(DocsumRequestTest, require_that_timeout_is_converted) {
    proto.set_timeout(500);
    convert();
    EXPECT_EQ(request.getTimeout().ms(), 500);
}

TEST_F(DocsumRequestTest, require_that_session_key_is_converted) {
    proto.set_session_key("my-session");
    convert();
    EXPECT_EQ(std::string(&request.sessionId[0], request.sessionId.size()), "my-session");
}

TEST_F(DocsumRequestTest, require_that_document_type_is_converted) {
    proto.set_document_type("music");
    convert();
    EXPECT_EQ(request.propertiesMap.matchProperties().lookup("documentdb", "searchdoctype").get(""), "music");
}

TEST_F(DocsumRequestTest, require_that_summary_class_is_converted) {
    proto.set_summary_class("prefetch");
    convert();
    EXPECT_EQ(request.resultClassName, "prefetch");
}

TEST_F(DocsumRequestTest, require_that_cache_query_is_converted) {
    proto.set_cache_query(true);
    convert();
    EXPECT_TRUE(request.propertiesMap.cacheProperties().lookup("query").found());
    EXPECT_FALSE(request.dumpFeatures);
}

TEST_F(DocsumRequestTest, require_that_dump_features_is_converted) {
    proto.set_dump_features(true);
    convert();
    EXPECT_FALSE(request.propertiesMap.cacheProperties().lookup("query").found());
    EXPECT_TRUE(request.dumpFeatures);
}

TEST_F(DocsumRequestTest, require_that_rank_profile_is_converted) {
    proto.set_rank_profile("mlr");
    convert();
    EXPECT_EQ(request.ranking, "mlr");
}

TEST_F(DocsumRequestTest, require_that_feature_overrides_are_converted) {
    auto *prop = proto.add_feature_overrides();
    prop->set_name("foo");
    prop->add_values("a");
    prop = proto.add_feature_overrides();
    prop->set_name("bar");
    prop->add_values("b");
    prop->add_values("c");
    auto *tprop = proto.add_tensor_feature_overrides();
    tprop->set_name("x1");
    tprop->set_value("[1,2,3]");
    tprop = proto.add_tensor_feature_overrides();
    tprop->set_name("y1");
    tprop->set_value("[4,5]");
    convert();
    auto foo = request.propertiesMap.featureOverrides().lookup("foo");
    EXPECT_EQ(foo.size(), 1u);
    EXPECT_EQ(foo.get(), "a");
    auto bar = request.propertiesMap.featureOverrides().lookup("bar");
    EXPECT_EQ(bar.size(), 2u);
    EXPECT_EQ(bar.get(), "b");
    EXPECT_EQ(bar.getAt(1), "c");
    auto x1 = request.propertiesMap.featureOverrides().lookup("x1");
    EXPECT_EQ(x1.size(), 1u);
    EXPECT_EQ(x1.get(), "[1,2,3]");
    auto y1 = request.propertiesMap.featureOverrides().lookup("y1");
    EXPECT_EQ(y1.size(), 1u);
    EXPECT_EQ(y1.get(), "[4,5]");
}

TEST_F(DocsumRequestTest, require_that_rank_properties_are_converted) {
    auto *prop = proto.add_rank_properties();
    prop->set_name("foo");
    prop->add_values("a");
    prop = proto.add_rank_properties();
    prop->set_name("bar");
    prop->add_values("b");
    prop->add_values("c");
    auto *tprop = proto.add_tensor_rank_properties();
    tprop->set_name("x1");
    tprop->set_value("[1,2,3]");
    tprop = proto.add_tensor_rank_properties();
    tprop->set_name("y1");
    tprop->set_value("[4,5]");
    convert();
    auto foo = request.propertiesMap.rankProperties().lookup("foo");
    EXPECT_EQ(foo.size(), 1u);
    EXPECT_EQ(foo.get(), "a");
    auto bar = request.propertiesMap.rankProperties().lookup("bar");
    EXPECT_EQ(bar.size(), 2u);
    EXPECT_EQ(bar.get(), "b");
    EXPECT_EQ(bar.getAt(1), "c");
    auto x1 = request.propertiesMap.rankProperties().lookup("x1");
    EXPECT_EQ(x1.size(), 1u);
    EXPECT_EQ(x1.get(), "[1,2,3]");
    auto y1 = request.propertiesMap.rankProperties().lookup("y1");
    EXPECT_EQ(y1.size(), 1u);
    EXPECT_EQ(y1.get(), "[4,5]");
}

TEST_F(DocsumRequestTest, require_that_highlight_terms_are_converted) {
    auto *prop = proto.add_highlight_terms();
    prop->set_name("foo");
    prop->add_values("a");
    prop = proto.add_highlight_terms();
    prop->set_name("bar");
    prop->add_values("b");
    prop->add_values("c");
    convert();
    auto foo = request.propertiesMap.highlightTerms().lookup("foo");
    EXPECT_EQ(foo.size(), 1u);
    EXPECT_EQ(foo.get(), "a");
    auto bar = request.propertiesMap.highlightTerms().lookup("bar");
    EXPECT_EQ(bar.size(), 2u);
    EXPECT_EQ(bar.get(), "b");
    EXPECT_EQ(bar.getAt(1), "c");
}

TEST_F(DocsumRequestTest, require_that_geo_location_is_converted) {
    proto.set_geo_location("x,y");
    convert();
    EXPECT_EQ(request.location, "x,y");
}

TEST_F(DocsumRequestTest, require_that_query_tree_blob_is_converted) {
    proto.set_query_tree_blob("query-tree-blob");
    convert();
    EXPECT_EQ(std::string(&request.stackDump[0], request.stackDump.size()), "query-tree-blob");
}

TEST_F(DocsumRequestTest, require_that_global_ids_are_converted) {
    constexpr size_t len = document::GlobalId::LENGTH;
    ASSERT_EQ(len, 12);
    char id0[len] = { 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12};
    char id1[len] = {11,12,13,14,15,16,17,18,19,20,21,22};
    char id2[len] = {21,22,23,24,25,26,27,28,29,30,31,32};
    proto.add_global_ids(id0, len);
    proto.add_global_ids(id1, len);
    proto.add_global_ids(id2, len);
    convert();
    ASSERT_EQ(request.hits.size(), 3);
    EXPECT_EQ(request.hits[0].gid, document::GlobalId(id0));
    EXPECT_EQ(request.hits[1].gid, document::GlobalId(id1));
    EXPECT_EQ(request.hits[2].gid, document::GlobalId(id2));
}

//-----------------------------------------------------------------------------

struct DocsumReplyTest : ::testing::Test {
    DocsumReply reply;
    Converter::ProtoDocsumReply proto;
    void convert() { Converter::docsum_reply_to_proto(reply, proto); }
};

TEST_F(DocsumReplyTest, require_that_slime_summaries_are_converted) {
    reply._root = std::make_unique<Slime>();
    auto &list = reply._root->setArray();
    auto &doc0 = list.addObject();
    doc0.setLong("my_field", 42);
    convert();
    const auto &mem = proto.slime_summaries();
    Slime slime;
    EXPECT_EQ(BinaryFormat::decode(Memory(mem.data(), mem.size()), slime), mem.size());
    EXPECT_EQ(slime.get()[0]["my_field"].asLong(), 42);
}

TEST_F(DocsumReplyTest, require_that_missing_root_slime_gives_empty_payload) {
    reply._root.reset();
    convert();
    EXPECT_EQ(proto.slime_summaries().size(), 0);
}

//-----------------------------------------------------------------------------

struct MonitorRequestTest : ::testing::Test {
    Converter::ProtoMonitorRequest proto;
    MonitorRequest request;
    void convert() { Converter::monitor_request_from_proto(proto, request); }
};

TEST_F(MonitorRequestTest, require_that_active_docs_are_always_requested) {
    convert();
    EXPECT_TRUE(request.reportActiveDocs);
}

//-----------------------------------------------------------------------------

struct MonitorReplyTest : ::testing::Test {
    MonitorReply reply;
    Converter::ProtoMonitorReply proto;
    void convert() { Converter::monitor_reply_to_proto(reply, proto); }
};

TEST_F(MonitorReplyTest, require_that_zero_timestamp_is_converted_to_online_false) {
    reply.timestamp = 0;
    convert();
    EXPECT_FALSE(proto.online());
}

TEST_F(MonitorReplyTest, require_that_nonzero_timestamp_is_converted_to_online_true) {
    reply.timestamp = 42;
    convert();
    EXPECT_TRUE(proto.online());
}

TEST_F(MonitorReplyTest, require_that_active_docs_is_converted) {
    reply.activeDocs = 12345;
    convert();
    EXPECT_EQ(proto.active_docs(), 12345);
}

TEST_F(MonitorReplyTest, require_that_distribution_key_is_converted) {
    reply.distribution_key = 7;
    convert();
    EXPECT_EQ(proto.distribution_key(), 7);
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()

#pragma GCC diagnostic pop
