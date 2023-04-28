// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/container/searchresult.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/vespalib/util/growablebytebuffer.h>
#include <variant>

using vespalib::FeatureValues;
using FeatureValue = vespalib::FeatureSet::Value;
using ConvertedValue = std::variant<double, std::string>;

namespace vdslib {

namespace {

std::vector<char> doc1_mf_data{'H', 'i'};
std::vector<char> doc2_mf_data{'T', 'h', 'e', 'r', 'e'};


std::vector<ConvertedValue> convert(vespalib::ConstArrayRef<FeatureValue> v) {
    std::vector<ConvertedValue> result;
    for (auto& iv : v) {
        if (iv.is_data()) {
            result.emplace_back(iv.as_data().make_stringref());
        } else {
            result.emplace_back(iv.as_double());
        }
    }
    return result;
}

std::vector<char> serialize(const SearchResult& sr) {
    auto serialized_size = sr.getSerializedSize();
    vespalib::GrowableByteBuffer buf;
    sr.serialize(buf);
    EXPECT_EQ(serialized_size, buf.position());
    return { buf.getBuffer(), buf.getBuffer() + buf.position() };
}

void deserialize(SearchResult& sr, vespalib::ConstArrayRef<char> buf)
{
    document::ByteBuffer dbuf(buf.data(), buf.size());
    sr.deserialize(dbuf);
    EXPECT_EQ(0, dbuf.getRemaining());
}

void populate(SearchResult& sr, FeatureValues& mf)
{
    sr.addHit(7, "doc1", 5);
    sr.addHit(8, "doc2", 7);
    mf.names.push_back("foo");
    mf.names.push_back("bar");
    mf.values.resize(4);
    mf.values[0].set_double(1.0);
    mf.values[1].set_data({doc1_mf_data.data(), doc1_mf_data.size()});
    mf.values[2].set_double(12.0);
    mf.values[3].set_data({doc2_mf_data.data(), doc2_mf_data.size()});
    sr.set_match_features(FeatureValues(mf));
}

void check_match_features(SearchResult& sr, const vespalib::string& label, bool sort_remap)
{
    SCOPED_TRACE(label);
    EXPECT_EQ((std::vector<ConvertedValue>{1.0, "Hi"}), convert(sr.get_match_feature_values(sort_remap ? 1 : 0)));
    EXPECT_EQ((std::vector<ConvertedValue>{12.0, "There"}), convert(sr.get_match_feature_values(sort_remap ? 0 : 1)));
}

void check_match_features(std::vector<char> buf, const vespalib::string& label, bool sort_remap)
{
    SearchResult sr;
    deserialize(sr, buf);
    check_match_features(sr, label, sort_remap);
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
    FeatureValues mf;
    populate(sr, mf);
    EXPECT_EQ(mf.names, sr.get_match_features().names);
    EXPECT_EQ(mf.values, sr.get_match_features().values);
    check_match_features(sr, "unsorted", false);
    sr.sort();
    // Sorting does not change the stored match features
    EXPECT_EQ(mf.names, sr.get_match_features().names);
    EXPECT_EQ(mf.values, sr.get_match_features().values);
    // Sorting affects retrieval of the stored matched features
    check_match_features(sr, "sorted", true);
}

TEST(SearchResultTest, test_deserialized_match_features)
{
    SearchResult sr;
    FeatureValues mf;
    populate(sr, mf);
    check_match_features(serialize(sr), "deserialized unsorted", false);
    sr.sort();
    check_match_features(serialize(sr), "deserialized sorted", true);
}

}
