// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/feature_resolver.h>
#include <vespa/searchvisitor/hitcollector.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/objects/nbostream.h>

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


namespace streaming {

class HitCollectorTest : public vespalib::TestApp
{
private:
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

public:
    HitCollectorTest();
    ~HitCollectorTest();
    int Main() override;
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
    EXPECT_EQUAL(rank, expRank);
    EXPECT_EQUAL(lDocId, expDocId);
}

void
HitCollectorTest::addHit(HitCollector &hc, uint32_t docId, double score, const char *sortData, size_t sortDataSize)
{
    document::Document::UP doc(new document::Document(_docType, DocumentId("id:ns:testdoc::")));
    StorageDocument::UP sdoc(new StorageDocument(std::move(doc), SharedFieldPathMap(), 0));
    ASSERT_TRUE(sdoc->valid());
    MatchData md(MatchData::params());
    hc.addHit(sdoc.get(), docId, md, score, sortData, sortDataSize);
    _backedHits.push_back(std::move(sdoc));
}

void
HitCollectorTest::testSimple()
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

void
HitCollectorTest::testGapsInDocId()
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

void
HitCollectorTest::testHeapProperty()
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

void
HitCollectorTest::testHeapPropertyWithSorting()
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

void
HitCollectorTest::testEmpty()
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

void
HitCollectorTest::testFeatureSet()
{
    HitCollector hc(3);

    addHit(hc, 0, 10);
    addHit(hc, 1, 50); // on heap
    addHit(hc, 2, 20);
    addHit(hc, 3, 40); // on heap
    addHit(hc, 4, 30); // on heap

    MyRankProgram rankProgram;
    FeatureResolver resolver(rankProgram.get_resolver());
    search::FeatureSet::SP sf = hc.getFeatureSet(rankProgram, resolver);

    EXPECT_EQUAL(sf->getNames().size(), 3u);
    EXPECT_EQUAL(sf->getNames()[0], "foo");
    EXPECT_EQUAL(sf->getNames()[1], "bar");
    EXPECT_EQUAL(sf->getNames()[2], "baz");
    EXPECT_EQUAL(sf->numFeatures(), 3u);
    EXPECT_EQUAL(sf->numDocs(), 3u);
    {
        const auto * f = sf->getFeaturesByDocId(1);
        ASSERT_TRUE(f != NULL);
        EXPECT_EQUAL(f[0].as_double(), 11); // 10 + docId
        EXPECT_EQUAL(f[1].as_double(), 31); // 30 + docId
    }
    {
        const auto * f = sf->getFeaturesByDocId(3);
        ASSERT_TRUE(f != NULL);
        EXPECT_TRUE(f[0].is_double());
        EXPECT_TRUE(!f[0].is_data());
        EXPECT_EQUAL(f[0].as_double(), 13);
        EXPECT_TRUE(f[1].is_double());
        EXPECT_TRUE(!f[1].is_data());
        EXPECT_EQUAL(f[1].as_double(), 33);
        EXPECT_TRUE(!f[2].is_double());
        EXPECT_TRUE(f[2].is_data());
        {
            nbostream buf(f[2].as_data().data, f[2].as_data().size);
            auto actual = spec_from_value(*SimpleValue::from_stream(buf));
            auto expect = TensorSpec("tensor(x{})").add({{"x", "a"}}, 23);
            EXPECT_EQUAL(actual, expect);
        }
    }
    {
        const auto * f = sf->getFeaturesByDocId(4);
        ASSERT_TRUE(f != NULL);
        EXPECT_EQUAL(f[0].as_double(), 14);
        EXPECT_EQUAL(f[1].as_double(), 34);
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

int
HitCollectorTest::Main()
{
    TEST_INIT("hitcollector_test");

    testSimple();
    testGapsInDocId();
    testHeapProperty();
    testHeapPropertyWithSorting();
    testEmpty();
    testFeatureSet();

    TEST_DONE();
}

} // namespace streaming

TEST_APPHOOK(streaming::HitCollectorTest)
