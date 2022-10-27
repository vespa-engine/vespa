// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/diskindex/disktermblueprint.h>
#include <vespa/searchlib/test/diskindex/testdiskindex.h>
#define ENABLE_GTEST_MIGRATION
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/searchlib/test/fakedata/fakeword.h>
#include <vespa/searchlib/diskindex/zcposocciterators.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/booleanmatchiteratorwrapper.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/test/fakedata/fpfactory.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <filesystem>
#include <set>

using search::BitVector;
using search::BitVectorIterator;
using search::diskindex::DiskIndex;
using search::diskindex::DiskTermBlueprint;
using search::diskindex::TestDiskIndex;
using search::diskindex::ZcRareWordPosOccIterator;
using search::fef::TermFieldMatchDataArray;
using search::index::DummyFileHeaderContext;
using search::index::PostingListHandle;
using search::index::Schema;
using search::query::SimpleStringTerm;
using search::queryeval::Blueprint;
using search::queryeval::BooleanMatchIteratorWrapper;
using search::queryeval::EmptyBlueprint;
using search::queryeval::EmptySearch;
using search::queryeval::ExecuteInfo;
using search::queryeval::FakeRequestContext;
using search::queryeval::FieldSpec;
using search::queryeval::LeafBlueprint;
using search::queryeval::SearchIterator;
using search::queryeval::SimpleResult;
using search::test::SearchIteratorVerifier;
using search::fakedata::FPFactory;
using search::fakedata::FakePosting;
using search::fakedata::FakeWord;
using search::fakedata::getFPFactory;
using LookupResult = DiskIndex::LookupResult;

namespace {

SimpleStringTerm
makeTerm(const std::string & term)
{
    return SimpleStringTerm(term, "field", 0, search::query::Weight(0));
}

class Verifier : public SearchIteratorVerifier {
public:
    Verifier(FakePosting::SP fp);
    ~Verifier();
    SearchIterator::UP create(bool strict) const override {
        (void) strict;
        return SearchIterator::UP(_fp->createIterator(_tfmda));
    }
private:
    TermFieldMatchData _tfmd;
    TermFieldMatchDataArray _tfmda;
    FakePosting::SP _fp;
};

Verifier::Verifier(FakePosting::SP fp)
    : _tfmd(),
      _tfmda(),
      _fp(std::move(fp))
{
    if (_fp) {
        _tfmd.setNeedNormalFeatures(_fp->enable_unpack_normal_features());
        _tfmd.setNeedInterleavedFeatures(_fp->enable_unpack_interleaved_features());
    }
    _tfmda.add(&_tfmd);
}

Verifier::~Verifier() = default;

struct EmptySettings
{
    bool _empty_field;
    bool _empty_doc;
    bool _empty_word;
    EmptySettings()
        : _empty_field(false),
          _empty_doc(false),
          _empty_word(false)
    {
    }
    EmptySettings empty_field() && { _empty_field = true; return *this; }
    EmptySettings empty_doc() && { _empty_doc = true; return *this; }
    EmptySettings empty_word() && { _empty_word = true; return *this; }
};

struct IOSettings
{
    bool _use_directio;
    bool _use_mmap;
    IOSettings()
        : _use_directio(false),
          _use_mmap(false)
    {
    }
    IOSettings use_directio() && { _use_directio = true; return *this; }
    IOSettings use_mmap() && { _use_mmap = true; return *this; }
};

class DiskIndexTest : public ::testing::Test, public TestDiskIndex {
private:
    FakeRequestContext _requestContext;

protected:
    void requireThatLookupIsWorking(const EmptySettings& empty_settings);
    void requireThatWeCanReadPostingList();
    void require_that_we_can_get_field_length_info();
    void requireThatWeCanReadBitVector();
    void requireThatBlueprintIsCreated();
    void requireThatBlueprintCanCreateSearchIterators();
    void requireThatSearchIteratorsConforms();
    void build_index(const IOSettings& io_settings, const EmptySettings& empty_settings);
    void test_empty_settings(const EmptySettings& empty_settings);
    void test_io_settings(const IOSettings& io_settings);
public:
    DiskIndexTest();
    ~DiskIndexTest();
};

DiskIndexTest::DiskIndexTest() = default;

DiskIndexTest::~DiskIndexTest() = default;

void
DiskIndexTest::requireThatSearchIteratorsConforms()
{
    FakePosting::SP tmp;
    Verifier verTmp(tmp);
    Schema schema;
    schema.addIndexField(Schema::IndexField("a", Schema::DataType::STRING));
    search::bitcompression::PosOccFieldsParams params;
    params.setSchemaParams(schema, 0);
    search::fakedata::FakeWord fw(verTmp.getDocIdLimit(), verTmp.getExpectedDocIds(), "a", params, 0);
    TermFieldMatchData md;
    TermFieldMatchDataArray tfmda;
    tfmda.add(&md);
    std::vector<const FakeWord *> v;
    v.push_back(&fw);
    std::set<std::string> ignored = { "MemTreeOcc", "MemTreeOcc2",
                                      "FilterOcc", "ZcFilterOcc",
                                      "ZcNoSkipFilterOcc", "ZcSkipFilterOcc",
                                      "ZcbFilterOcc",
                                      "EGCompr64FilterOcc", "EGCompr64LEFilterOcc",
                                      "EGCompr64NoSkipFilterOcc", "EGCompr64SkipFilterOcc" };
    for (auto postingType : search::fakedata::getPostingTypes()) {
        SCOPED_TRACE(postingType);
        if (ignored.find(postingType) == ignored.end()) {
            std::unique_ptr<FPFactory> ff(getFPFactory(postingType, schema));
            ff->setup(v);
            FakePosting::SP f(ff->make(fw));
            Verifier verifier(f);
            verifier.verify();
        }
    }
}

void
DiskIndexTest::requireThatLookupIsWorking(const EmptySettings& empty_settings)
{
    auto fieldEmpty = empty_settings._empty_field;
    auto docEmpty = empty_settings._empty_doc;
    auto wordEmpty = empty_settings._empty_word;
    uint32_t f1(_schema.getIndexFieldId("f1"));
    uint32_t f2(_schema.getIndexFieldId("f2"));
    uint32_t f3(_schema.getIndexFieldId("f3"));
    LookupResult::UP r;
    r = _index->lookup(f1, "not");
    EXPECT_TRUE(!r || r->counts._numDocs == 0);
    r = _index->lookup(f1, "w1not");
    EXPECT_TRUE(!r || r->counts._numDocs == 0);
    r = _index->lookup(f1, "wnot");
    EXPECT_TRUE(!r || r->counts._numDocs == 0);
    { // field 'f1'
        r = _index->lookup(f1, "w1");
        if (wordEmpty || fieldEmpty || docEmpty) {
            EXPECT_TRUE(!r || r->counts._numDocs == 0);
        } else {
            EXPECT_EQ(1u, r->wordNum);
            EXPECT_EQ(2u, r->counts._numDocs);
        }
        r = _index->lookup(f1, "w2");
        EXPECT_TRUE(!r || r->counts._numDocs == 0);
    }
    { // field 'f2'
        r = _index->lookup(f2, "w1");
        if (wordEmpty || fieldEmpty || docEmpty) {
            EXPECT_TRUE(!r || r->counts._numDocs == 0);
        } else {
            EXPECT_EQ(1u, r->wordNum);
            EXPECT_EQ(3u, r->counts._numDocs);
        }
        r = _index->lookup(f2, "w2");
        if (wordEmpty || fieldEmpty || docEmpty) {
            EXPECT_TRUE(!r || r->counts._numDocs == 0);
        } else {
            EXPECT_EQ(2u, r->wordNum);
            EXPECT_EQ(17u, r->counts._numDocs);
        }
    }
    { // field 'f3' doesn't exist
        r = _index->lookup(f3, "w1");
        EXPECT_TRUE(!r || r->counts._numDocs == 0);
        r = _index->lookup(f3, "w2");
        EXPECT_TRUE(!r || r->counts._numDocs == 0);
    }
}

void
DiskIndexTest::requireThatWeCanReadPostingList()
{
    TermFieldMatchDataArray mda;
    { // field 'f1'
        LookupResult::UP r = _index->lookup(0, "w1");
        PostingListHandle::UP h = _index->readPostingList(*r);
        SearchIterator * sb = h->createIterator(r->counts, mda);
        EXPECT_EQ(SimpleResult({1,3}), SimpleResult().search(*sb));
        delete sb;
    }
}

void
DiskIndexTest::require_that_we_can_get_field_length_info()
{
    auto info = _index->get_field_length_info("f1");
    EXPECT_EQ(3.5, info.get_average_field_length());
    EXPECT_EQ(21u, info.get_num_samples());
    info = _index->get_field_length_info("f2");
    EXPECT_EQ(4.0, info.get_average_field_length());
    EXPECT_EQ(23u, info.get_num_samples());
    info = _index->get_field_length_info("f3");
    EXPECT_EQ(0.0, info.get_average_field_length());
    EXPECT_EQ(0u, info.get_num_samples());
}

void
DiskIndexTest::requireThatWeCanReadBitVector()
{
    { // word 'w1'
        LookupResult::UP r = _index->lookup(1, "w1");
        // not bit vector for 'w1'
        EXPECT_TRUE(_index->readBitVector(*r).get() == NULL);
    }
    { // word 'w2'
        BitVector::UP exp(BitVector::create(32));
        for (uint32_t docId = 1; docId < 18; ++docId) exp->setBit(docId);
        { // field 'f2'
            LookupResult::UP r =
                _index->lookup(1, "w2");
            BitVector::UP bv = _index->readBitVector(*r);
            EXPECT_TRUE(bv.get() != NULL);
            EXPECT_TRUE(*bv == *exp);
        }
    }
}

void
DiskIndexTest::requireThatBlueprintIsCreated()
{
    { // unknown field
        Blueprint::UP b =
            _index->createBlueprint(_requestContext, FieldSpec("none", 0, 0), makeTerm("w1"));
        EXPECT_TRUE(dynamic_cast<EmptyBlueprint *>(b.get()) != NULL);
    }
    { // unknown word
        Blueprint::UP b =
            _index->createBlueprint(_requestContext, FieldSpec("f1", 0, 0), makeTerm("none"));
        EXPECT_TRUE(dynamic_cast<EmptyBlueprint *>(b.get()) != NULL);
    }
    { // known field & word with hits
        Blueprint::UP b =
            _index->createBlueprint(_requestContext, FieldSpec("f1", 0, 0), makeTerm("w1"));
        EXPECT_TRUE(dynamic_cast<DiskTermBlueprint *>(b.get()) != NULL);
        EXPECT_EQ(2u, b->getState().estimate().estHits);
        EXPECT_TRUE(!b->getState().estimate().empty);
    }
    { // known field & word without hits
        Blueprint::UP b =
            _index->createBlueprint(_requestContext, FieldSpec("f1", 0, 0), makeTerm("w2"));
//        std::cerr << "BP = " << typeid(*b).name() << std::endl;
        EXPECT_TRUE((dynamic_cast<DiskTermBlueprint *>(b.get()) != NULL) ||
                    (dynamic_cast<EmptyBlueprint *>(b.get()) != NULL));
        EXPECT_EQ(0u, b->getState().estimate().estHits);
        EXPECT_TRUE(b->getState().estimate().empty);
    }
}

void
DiskIndexTest::requireThatBlueprintCanCreateSearchIterators()
{
    TermFieldMatchData md;
    TermFieldMatchDataArray mda;
    mda.add(&md);
    Blueprint::UP b;
    SearchIterator::UP s;
    SimpleResult result_f1_w1({1,3});
    SimpleResult result_f1_w2;
    SimpleResult result_f2_w2({1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17});
    auto upper_bound = Blueprint::FilterConstraint::UPPER_BOUND;
    { // bit vector due to isFilter
        b = _index->createBlueprint(_requestContext, FieldSpec("f2", 0, 0, true), makeTerm("w2"));
        b->fetchPostings(search::queryeval::ExecuteInfo::TRUE);
        auto& leaf_b = dynamic_cast<LeafBlueprint&>(*b);
        s = leaf_b.createLeafSearch(mda, true);
        EXPECT_TRUE(dynamic_cast<BitVectorIterator *>(s.get()) != NULL);
        EXPECT_EQ(result_f2_w2, SimpleResult().search(*s));
        EXPECT_EQ(result_f2_w2, SimpleResult().search(*leaf_b.createFilterSearch(true, upper_bound)));
    }
    { // bit vector due to no ranking needed
        b = _index->createBlueprint(_requestContext, FieldSpec("f2", 0, 0, false), makeTerm("w2"));
        b->fetchPostings(ExecuteInfo::TRUE);
        auto& leaf_b = dynamic_cast<LeafBlueprint&>(*b);
        s = leaf_b.createLeafSearch(mda, true);
        EXPECT_FALSE(dynamic_cast<BitVectorIterator *>(s.get()) != NULL);
        TermFieldMatchData md2;
        md2.tagAsNotNeeded();
        TermFieldMatchDataArray mda2;
        mda2.add(&md2);
        EXPECT_TRUE(mda2[0]->isNotNeeded());
        s = (dynamic_cast<LeafBlueprint *>(b.get()))->createLeafSearch(mda2, true);
        EXPECT_TRUE(dynamic_cast<BitVectorIterator *>(s.get()) != NULL);
        EXPECT_EQ(result_f2_w2, SimpleResult().search(*s));
        EXPECT_EQ(result_f2_w2, SimpleResult().search(*leaf_b.createFilterSearch(true, upper_bound)));
    }
    { // fake bit vector
        b = _index->createBlueprint(_requestContext, FieldSpec("f1", 0, 0, true), makeTerm("w2"));
//        std::cerr << "BP = " << typeid(*b).name() << std::endl;
        b->fetchPostings(ExecuteInfo::TRUE);
        auto& leaf_b = dynamic_cast<LeafBlueprint&>(*b);
        s = leaf_b.createLeafSearch(mda, true);
//        std::cerr << "SI = " << typeid(*s).name() << std::endl;
        EXPECT_TRUE((dynamic_cast<BooleanMatchIteratorWrapper *>(s.get()) != NULL) ||
                    dynamic_cast<EmptySearch *>(s.get()));
        EXPECT_EQ(result_f1_w2, SimpleResult().search(*s));
        EXPECT_EQ(result_f1_w2, SimpleResult().search(*leaf_b.createFilterSearch(true, upper_bound)));
    }
    { // posting list iterator
        b = _index->createBlueprint(_requestContext, FieldSpec("f1", 0, 0), makeTerm("w1"));
        b->fetchPostings(ExecuteInfo::TRUE);
        auto& leaf_b = dynamic_cast<LeafBlueprint&>(*b);
        s = leaf_b.createLeafSearch(mda, true);
        ASSERT_TRUE((dynamic_cast<ZcRareWordPosOccIterator<true, false> *>(s.get()) != NULL));
        EXPECT_EQ(result_f1_w1, SimpleResult().search(*s));
        EXPECT_EQ(result_f1_w1, SimpleResult().search(*leaf_b.createFilterSearch(true, upper_bound)));
    }
}

void
DiskIndexTest::build_index(const IOSettings& io_settings, const EmptySettings& empty_settings)
{
    vespalib::asciistream name;
    int io_settings_num = 1;
    if (io_settings._use_directio) {
        io_settings_num += 1;
    }
    if (io_settings._use_mmap) {
        io_settings_num += 2;
    }
    name << "index/" << io_settings_num;
    if (empty_settings._empty_field) {
        name << "fe";
    } else {
        buildSchema();
    }
    if (empty_settings._empty_doc) {
        name << "de";
    }
    if (empty_settings._empty_word) {
        name << "we";
    }
    openIndex(name.str(), io_settings._use_directio, io_settings._use_mmap, empty_settings._empty_field, empty_settings._empty_doc, empty_settings._empty_word);
}

void
DiskIndexTest::test_empty_settings(const EmptySettings& empty_settings)
{
    build_index(IOSettings(), empty_settings);
    requireThatLookupIsWorking(empty_settings);
}

void
DiskIndexTest::test_io_settings(const IOSettings& io_settings)
{
    EmptySettings empty_settings;
    build_index(io_settings, empty_settings);
    requireThatLookupIsWorking(empty_settings);
    requireThatWeCanReadPostingList();
    require_that_we_can_get_field_length_info();
    requireThatWeCanReadBitVector();
    requireThatBlueprintIsCreated();
    requireThatBlueprintCanCreateSearchIterators();
}

TEST_F(DiskIndexTest, empty_settings_empty_field_empty_doc_empty_word)
{
    test_empty_settings(EmptySettings().empty_field().empty_doc().empty_word());
}

TEST_F(DiskIndexTest, empty_settings_empty_field_empty_doc)
{
    test_empty_settings(EmptySettings().empty_field().empty_doc());
}

TEST_F(DiskIndexTest, empty_settings_empty_field_empty_word)
{
    test_empty_settings(EmptySettings().empty_field().empty_word());
}

TEST_F(DiskIndexTest, empty_settings_empty_field)
{
    test_empty_settings(EmptySettings().empty_field());
}

TEST_F(DiskIndexTest, empty_settings_empty_doc_empty_word)
{
    test_empty_settings(EmptySettings().empty_doc().empty_word());
}

TEST_F(DiskIndexTest, empty_settings_empty_doc)
{
    test_empty_settings(EmptySettings().empty_doc());
}

TEST_F(DiskIndexTest, empty_settings_empty_word)
{
    test_empty_settings(EmptySettings().empty_word());
}

TEST_F(DiskIndexTest, io_settings_normal)
{
    test_io_settings(IOSettings());
}

TEST_F(DiskIndexTest, io_settings_directio)
{
    test_io_settings(IOSettings().use_directio());
}

TEST_F(DiskIndexTest, io_settings_mmap)
{
    test_io_settings(IOSettings().use_mmap());
}

TEST_F(DiskIndexTest, io_settings_directio_mmap)
{
    test_io_settings(IOSettings().use_directio().use_mmap());
}

TEST_F(DiskIndexTest, search_iterators_conformance)
{
    requireThatSearchIteratorsConforms();
}

}

int
main(int argc, char* argv[])
{
    if (argc > 0) {
        DummyFileHeaderContext::setCreator(argv[0]);
    }
    ::testing::InitGoogleTest(&argc, argv);
    std::filesystem::path index_path("index");
    std::filesystem::remove_all(index_path);
    std::filesystem::create_directory(index_path);
    auto rval = RUN_ALL_TESTS();
    std::filesystem::remove_all(index_path);
    return rval;
}
