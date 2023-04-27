// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/container/searchresult.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::FeatureValues;
using FeatureValue = vespalib::FeatureSet::Value;

namespace vdslib {

namespace {

std::vector<double> to_doubles(vespalib::ConstArrayRef<FeatureValue> v) {
    std::vector<double> result;
    for (auto& iv : v) {
        EXPECT_TRUE(iv.is_double());
        result.emplace_back(iv.as_double());
    }
    return result;
}

}

TEST(SearchResultTest, test_simple)
{
    SearchResult a;
    EXPECT_EQ(0, a.getHitCount());
    a.addHit(7, "doc1", 6);
    ASSERT_EQ(1, a.getHitCount());
    a.addHit(8, "doc2", 7);
    ASSERT_EQ(2, a.getHitCount());
    const char *docId;
    SearchResult::RankType r;
    EXPECT_EQ(7, a.getHit(0, docId, r));
    EXPECT_EQ("doc1", std::string(docId));
    EXPECT_EQ(6, r);
    EXPECT_EQ(8, a.getHit(1, docId, r));
    EXPECT_EQ("doc2", std::string(docId));
    EXPECT_EQ(7, r);
    a.sort();
    EXPECT_EQ(8, a.getHit(0, docId, r));
    EXPECT_EQ("doc2", std::string(docId));
    EXPECT_EQ(7, r);
    EXPECT_EQ(7, a.getHit(1, docId, r));
    EXPECT_EQ("doc1", std::string(docId));
    EXPECT_EQ(6, r);
}

TEST(SearchResultTest, test_simple_sort_data)
{
    SearchResult a;
    EXPECT_EQ(0, a.getHitCount());
    a.addHit(7, "doc1", 6, "abce", 4);
    ASSERT_EQ(1, a.getHitCount());
    a.addHit(8, "doc2", 7, "abcde", 5);
    ASSERT_EQ(2, a.getHitCount());
    const char *docId;
    SearchResult::RankType r;
    EXPECT_EQ(7, a.getHit(0, docId, r));
    EXPECT_EQ("doc1", std::string(docId));
    EXPECT_EQ(6, r);
    const void *buf;
    size_t sz;
    a.getSortBlob(0, buf, sz);
    EXPECT_EQ(4, sz);
    EXPECT_TRUE(memcmp("abce", buf, sz) == 0);
    EXPECT_EQ(8, a.getHit(1, docId, r));
    EXPECT_EQ("doc2", std::string(docId));
    EXPECT_EQ(7, r);
    a.getSortBlob(1, buf, sz);
    EXPECT_EQ(5, sz);
    EXPECT_TRUE(memcmp("abcde", buf, sz) == 0);
    a.sort();
    EXPECT_EQ(8, a.getHit(0, docId, r));
    EXPECT_EQ("doc2", std::string(docId));
    EXPECT_EQ(7, r);
    a.getSortBlob(0, buf, sz);
    EXPECT_EQ(5, sz);
    EXPECT_EQ(7, a.getHit(1, docId, r));
    EXPECT_EQ("doc1", std::string(docId));
    EXPECT_EQ(6, r);
    a.getSortBlob(1, buf, sz);
    EXPECT_EQ(4, sz);
}

TEST(SearchResultTest, test_match_features)
{
    SearchResult sr;
    sr.addHit(7, "doc1", 5);
    sr.addHit(8, "doc2", 7);
    FeatureValues mf;
    mf.names.push_back("foo");
    mf.names.push_back("bar");
    mf.values.resize(4);
    mf.values[0].set_double(1.0);
    mf.values[1].set_double(7.0);
    mf.values[2].set_double(12.0);
    mf.values[3].set_double(13.0);
    sr.set_match_features(FeatureValues(mf));
    EXPECT_EQ(mf.names, sr.get_match_features().names);
    EXPECT_EQ(mf.values, sr.get_match_features().values);
    EXPECT_EQ((std::vector<double>{  1.0,  7.0}), to_doubles(sr.get_match_feature_values(0)));
    EXPECT_EQ((std::vector<double>{ 12.0, 13.0}), to_doubles(sr.get_match_feature_values(1)));
    sr.sort();
    // Sorting does not change the stored match features
    EXPECT_EQ(mf.names, sr.get_match_features().names);
    EXPECT_EQ(mf.values, sr.get_match_features().values);
    // Sorting affects retrieval of the stored matched features
    EXPECT_EQ((std::vector<double>{ 12.0, 13.0}), to_doubles(sr.get_match_feature_values(0)));
    EXPECT_EQ((std::vector<double>{  1.0,  7.0}), to_doubles(sr.get_match_feature_values(1)));
}

}
