// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/feature_resolver.h>
#include <vespa/searchvisitor/hitcollector.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace document;
using namespace search::fef;
using namespace vespalib;
using namespace vdslib;
using namespace vsm;
using vespalib::nbostream;
using vespalib::eval::DoubleValue;
using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::make_string_short::fmt;

using FeatureValue = FeatureSet::Value;

namespace {

double as_double(const FeatureValue& v) {
    EXPECT_TRUE(v.is_double());
    return v.as_double();
}

TensorSpec as_spec(const FeatureValue& v) {
    EXPECT_TRUE(v.is_data());
    auto mem = v.as_data();
    nbostream buf(mem.data, mem.size);
    return spec_from_value(*SimpleValue::from_stream(buf));
}

ConstArrayRef<FeatureValue> as_value_slice(FeatureValues& mf, uint32_t index, uint32_t num_features)
{
    return { mf.values.data() + index * num_features, num_features };
}

void check_match_features(ConstArrayRef<FeatureValue> v, uint32_t docid)
{
    SCOPED_TRACE(fmt("Checking docid %u for expected match features", docid));
    // The following values should have been set by MyRankProgram::run()
    EXPECT_EQ(10 + docid, as_double(v[0]));
    EXPECT_EQ(30 + docid, as_double(v[1]));
    EXPECT_EQ(TensorSpec("tensor(x{})").add({{"x", "a"}}, 20 + docid), as_spec(v[2]));
}

}

namespace streaming {

class HitCollectorTest : public ::testing::Test
{
protected:
    void assertHit(SearchResult::RankType expRank, uint32_t hitNo, SearchResult & rs);
    void assertHit(SearchResult::RankType expRank, uint32_t expDocId, uint32_t hitNo, SearchResult & rs);
    void addHit(HitCollector &hc, uint32_t docId, double score,
                const char *sortData = nullptr, size_t sortDataSize = 0);
    void testSimple();
    void testGapsInDocId();
    void testHeapProperty();
    void testHeapPropertyWithSorting();
    void testEmpty();
    void testFeatureSet();

    DocumentType _docType;
    std::vector<vsm::StorageDocument::UP> _backedHits;

    HitCollectorTest();
    ~HitCollectorTest() override;
};

HitCollectorTest::HitCollectorTest()
    : _docType("testdoc", 0)
{
}

HitCollectorTest::~HitCollectorTest() {}

void
HitCollectorTest::assertHit(SearchResult::RankType expRank, uint32_t hitNo, SearchResult & rs)
{
    assertHit(expRank, hitNo, hitNo, rs);
}

void
HitCollectorTest::assertHit(SearchResult::RankType expRank, uint32_t expDocId, uint32_t hitNo, SearchResult & rs)
{
    //std::cout << "assertHit(" << expRank << ", " << expDocId << ")" << std::endl;
    uint32_t lDocId;
    const char * gDocId;
    SearchResult::RankType rank;
    lDocId = rs.getHit(hitNo, gDocId, rank);
    EXPECT_EQ(rank, expRank);
    EXPECT_EQ(lDocId, expDocId);
}

void
HitCollectorTest::addHit(HitCollector &hc, uint32_t docId, double score, const char *sortData, size_t sortDataSize)
{
    auto doc = document::Document::make_without_repo(_docType, DocumentId("id:ns:testdoc::"));
    auto sdoc = std::make_unique<StorageDocument>(std::move(doc), SharedFieldPathMap(), 0);
    ASSERT_TRUE(sdoc->valid());
    MatchData md(MatchData::params());
    hc.addHit(sdoc.get(), docId, md, score, sortData, sortDataSize);
    _backedHits.push_back(std::move(sdoc));
}

TEST_F(HitCollectorTest, simple)
{
    HitCollector hc(5);

    // add hits to hit collector
    for (uint32_t i = 0; i < 5; ++i) {
        addHit(hc, i, 10 + i);
    }
    // merge from match data heap and fill search result
    for (size_t i = 0; i < 2; ++i) { // try it twice
        SearchResult sr;
        hc.fillSearchResult(sr);
        ASSERT_TRUE(sr.getHitCount() == 5);
        assertHit(10, 0, sr);
        assertHit(11, 1, sr);
        assertHit(12, 2, sr);
        assertHit(13, 3, sr);
        assertHit(14, 4, sr);
    }
}

TEST_F(HitCollectorTest, gaps_in_docid)
{
    HitCollector hc(5);

    // add hits to hit collector
    for (uint32_t i = 0; i < 5; ++i) {
        addHit(hc, i * 2, i * 2 + 10);
    }

    // merge from heap into search result
    SearchResult sr;
    hc.fillSearchResult(sr);

    ASSERT_TRUE(sr.getHitCount() == 5);
    assertHit(10, 0, 0, sr);
    assertHit(12, 2, 1, sr);
    assertHit(14, 4, 2, sr);
    assertHit(16, 6, 3, sr);
    assertHit(18, 8, 4, sr);
}

TEST_F(HitCollectorTest, heap_property)
{
    {
        HitCollector hc(3);
        // add hits (low to high)
        for (uint32_t i = 0; i < 6; ++i) {
            addHit(hc, i, i + 10);
        }
        SearchResult sr;
        hc.fillSearchResult(sr);
        ASSERT_TRUE(sr.getHitCount() == 3);
        assertHit(13, 3, 0, sr);
        assertHit(14, 4, 1, sr);
        assertHit(15, 5, 2, sr);
    }
    {
        HitCollector hc(3);
        // add hits (high to low)
        for (uint32_t i = 0; i < 6; ++i) {
            addHit(hc, i, 10 - i);
        }
        SearchResult sr;
        hc.fillSearchResult(sr);
        ASSERT_TRUE(sr.getHitCount() == 3);
        assertHit(10, 0, 0, sr);
        assertHit(9,  1, 1, sr);
        assertHit(8,  2, 2, sr);
    }
    {
        HitCollector hc(3);
        // add hits (same rank score)
        for (uint32_t i = 0; i < 6; ++i) {
            addHit(hc, i, 10);
        }
        SearchResult sr;
        hc.fillSearchResult(sr);
        ASSERT_TRUE(sr.getHitCount() == 3);
        assertHit(10, 0, 0, sr);
        assertHit(10, 1, 1, sr);
        assertHit(10, 2, 2, sr);
    }
}

TEST_F(HitCollectorTest, heap_property_with_sorting)
{
    std::vector<char> sortData;
    sortData.push_back('a');
    sortData.push_back('b');
    sortData.push_back('c');
    sortData.push_back('d');
    sortData.push_back('e');
    sortData.push_back('f');
    {
        HitCollector hc(3);
        // add hits ('a' is sorted/ranked better than 'b')
        for (uint32_t i = 0; i < 6; ++i) {
            addHit(hc, i, i + 10, &sortData[i], 1);
        }
        SearchResult sr;
        hc.fillSearchResult(sr);
        ASSERT_TRUE(sr.getHitCount() == 3);
        assertHit(10, 0, 0, sr);
        assertHit(11, 1, 1, sr);
        assertHit(12, 2, 2, sr);
    }
    {
        HitCollector hc(3);
        // add hits ('a' is sorted/ranked better than 'b')
        for (uint32_t i = 0; i < 6; ++i) {
            addHit(hc, i, i + 10, &sortData[5 - i], 1);
        }
        SearchResult sr;
        hc.fillSearchResult(sr);
        ASSERT_TRUE(sr.getHitCount() == 3);
        assertHit(13, 3, 0, sr);
        assertHit(14, 4, 1, sr);
        assertHit(15, 5, 2, sr);
    }
    {
        HitCollector hc(3);
        // add hits (same sort blob)
        for (uint32_t i = 0; i < 6; ++i) {
            addHit(hc, i, 10, &sortData[0], 1);
        }
        SearchResult sr;
        hc.fillSearchResult(sr);
        ASSERT_TRUE(sr.getHitCount() == 3);
        assertHit(10, 0, 0, sr);
        assertHit(10, 1, 1, sr);
        assertHit(10, 2, 2, sr);
    }
}

TEST_F(HitCollectorTest, empty)
{
    HitCollector hc(0);
    addHit(hc, 0, 0);
    SearchResult rs;
    hc.fillSearchResult(rs);
    ASSERT_TRUE(rs.getHitCount() == 0);
}

class MyRankProgram : public HitCollector::IRankProgram
{
private:
    Value::UP _boxed_double;
    Value::UP _tensor;
    NumberOrObject _fooValue;
    NumberOrObject _barValue;
    NumberOrObject _bazValue;

public:
    MyRankProgram()
        : _boxed_double(),
          _tensor(),
          _fooValue(),
          _barValue(),
          _bazValue()
    {}
    ~MyRankProgram();
    virtual void run(uint32_t docid, const std::vector<search::fef::TermFieldMatchData> &) override {
        _boxed_double = std::make_unique<DoubleValue>(docid + 30);
        _tensor = SimpleValue::from_spec(TensorSpec("tensor(x{})").add({{"x", "a"}}, docid + 20));
        _fooValue.as_number = docid + 10;
        _barValue.as_object = *_boxed_double;
        _bazValue.as_object = *_tensor;
    }
  
    FeatureResolver get_resolver() {
        FeatureResolver resolver(2);
        resolver.add("foo", LazyValue(&_fooValue), false);
        resolver.add("bar", LazyValue(&_barValue), true);
        resolver.add("baz", LazyValue(&_bazValue), true);
        return resolver;
    }
};
MyRankProgram::~MyRankProgram() = default;

TEST_F(HitCollectorTest, feature_set)
{
    HitCollector hc(3);

    addHit(hc, 0, 10);
    addHit(hc, 1, 50); // on heap
    addHit(hc, 2, 20);
    addHit(hc, 3, 40); // on heap
    addHit(hc, 4, 30); // on heap

    MyRankProgram rankProgram;
    FeatureResolver resolver(rankProgram.get_resolver());
    search::StringStringMap renames;
    renames["bar"] = "qux";
    vespalib::FeatureSet::SP sf = hc.getFeatureSet(rankProgram, resolver, renames);

    EXPECT_EQ(sf->getNames().size(), 3u);
    EXPECT_EQ(sf->getNames()[0], "foo");
    EXPECT_EQ(sf->getNames()[1], "qux");
    EXPECT_EQ(sf->getNames()[2], "baz");
    EXPECT_EQ(sf->numFeatures(), 3u);
    EXPECT_EQ(sf->numDocs(), 3u);
    {
        const auto * f = sf->getFeaturesByDocId(1);
        ASSERT_TRUE(f != NULL);
        EXPECT_EQ(f[0].as_double(), 11); // 10 + docId
        EXPECT_EQ(f[1].as_double(), 31); // 30 + docId
    }
    {
        const auto * f = sf->getFeaturesByDocId(3);
        ASSERT_TRUE(f != NULL);
        EXPECT_TRUE(f[0].is_double());
        EXPECT_TRUE(!f[0].is_data());
        EXPECT_EQ(f[0].as_double(), 13);
        EXPECT_TRUE(f[1].is_double());
        EXPECT_TRUE(!f[1].is_data());
        EXPECT_EQ(f[1].as_double(), 33);
        EXPECT_TRUE(!f[2].is_double());
        EXPECT_TRUE(f[2].is_data());
        {
            nbostream buf(f[2].as_data().data, f[2].as_data().size);
            auto actual = spec_from_value(*SimpleValue::from_stream(buf));
            auto expect = TensorSpec("tensor(x{})").add({{"x", "a"}}, 23);
            EXPECT_EQ(actual, expect);
        }
    }
    {
        const auto * f = sf->getFeaturesByDocId(4);
        ASSERT_TRUE(f != NULL);
        EXPECT_EQ(f[0].as_double(), 14);
        EXPECT_EQ(f[1].as_double(), 34);
    }
    ASSERT_TRUE(sf->getFeaturesByDocId(0) == NULL);
    ASSERT_TRUE(sf->getFeaturesByDocId(2) == NULL);

    SearchResult sr;
    hc.fillSearchResult(sr);
    ASSERT_TRUE(sr.getHitCount() == 3);
    assertHit(50, 1, 0, sr);
    assertHit(40, 3, 1, sr);
    assertHit(30, 4, 2, sr);
}

TEST_F(HitCollectorTest, match_features)
{
    HitCollector hc(3);

    addHit(hc, 0, 10);
    addHit(hc, 1, 50); // on heap
    addHit(hc, 2, 20);
    addHit(hc, 3, 40); // on heap
    addHit(hc, 4, 30); // on heap

    MyRankProgram rankProgram;
    FeatureResolver resolver(rankProgram.get_resolver());
    search::StringStringMap renames;
    renames["bar"] = "qux";
    auto mf = hc.get_match_features(rankProgram, resolver, renames);
    auto num_features = resolver.num_features();

    EXPECT_EQ(num_features, mf.names.size());
    EXPECT_EQ("foo", mf.names[0]);
    EXPECT_EQ("qux", mf.names[1]);
    EXPECT_EQ("baz", mf.names[2]);
    EXPECT_EQ(num_features * 3, mf.values.size());
    check_match_features(as_value_slice(mf, 0, num_features), 1);
    check_match_features(as_value_slice(mf, 1, num_features), 3);
    check_match_features(as_value_slice(mf, 2, num_features), 4);
}

} // namespace streaming

GTEST_MAIN_RUN_ALL_TESTS()
