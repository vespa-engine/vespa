// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/diskindex/disktermblueprint.h>
#include <vespa/searchlib/test/diskindex/testdiskindex.h>
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/searchlib/test/fakedata/fakeword.h>
#include <vespa/searchlib/diskindex/zcposocciterators.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/booleanmatchiteratorwrapper.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/test/fakedata/fpfactory.h>
#include <vespa/vespalib/io/fileutil.h>
#include <iostream>
#include <set>

using search::BitVectorIterator;
using namespace search::fef;
using namespace search::index;
using namespace search::query;
using namespace search::queryeval;
using namespace search::queryeval::blueprint;
using search::test::SearchIteratorVerifier;
using namespace search::fakedata;

namespace search {
namespace diskindex {

typedef DiskIndex::LookupResult LookupResult;

std::string
toString(SearchIterator & sb)
{
    std::ostringstream oss;
    bool first = true;
    for (sb.seek(1u); ! sb.isAtEnd(); sb.seek(sb.getDocId() + 1)) {
        if (!first) oss << ",";
        oss << sb.getDocId();
        first = false;
    }
    return oss.str();
}

SimpleStringTerm
makeTerm(const std::string & term)
{
    return SimpleStringTerm(term, "field", 0, search::query::Weight(0));
}

class Test : public vespalib::TestApp, public TestDiskIndex {
private:
    FakeRequestContext _requestContext;

    void requireThatLookupIsWorking(bool fieldEmpty, bool docEmpty, bool wordEmpty);
    void requireThatWeCanReadPostingList();
    void require_that_we_can_get_field_length_info();
    void requireThatWeCanReadBitVector();
    void requireThatBlueprintIsCreated();
    void requireThatBlueprintCanCreateSearchIterators();
    void requireThatSearchIteratorsConforms();
public:
    Test();
    ~Test();
    int Main() override;
};

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

void
Test::requireThatSearchIteratorsConforms()
{
    FakePosting::SP tmp;
    Verifier verTmp(tmp);
    Schema schema;
    schema.addIndexField(Schema::IndexField("a", Schema::DataType::STRING));
    bitcompression::PosOccFieldsParams params;
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
        if (ignored.find(postingType) == ignored.end()) {
            std::cerr << "Verifying " << postingType << std::endl;
            std::unique_ptr<FPFactory> ff(getFPFactory(postingType, schema));
            ff->setup(v);
            FakePosting::SP f(ff->make(fw));
            Verifier verifier(f);
            TEST_DO(verifier.verify());
        }
    }
}

void
Test::requireThatLookupIsWorking(bool fieldEmpty,
                                 bool docEmpty,
                                 bool wordEmpty)
{
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
            EXPECT_EQUAL(1u, r->wordNum);
            EXPECT_EQUAL(2u, r->counts._numDocs);
        }
        r = _index->lookup(f1, "w2");
        EXPECT_TRUE(!r || r->counts._numDocs == 0);
    }
    { // field 'f2'
        r = _index->lookup(f2, "w1");
        if (wordEmpty || fieldEmpty || docEmpty) {
            EXPECT_TRUE(!r || r->counts._numDocs == 0);
        } else {
            EXPECT_EQUAL(1u, r->wordNum);
            EXPECT_EQUAL(3u, r->counts._numDocs);
        }
        r = _index->lookup(f2, "w2");
        if (wordEmpty || fieldEmpty || docEmpty) {
            EXPECT_TRUE(!r || r->counts._numDocs == 0);
        } else {
            EXPECT_EQUAL(2u, r->wordNum);
            EXPECT_EQUAL(17u, r->counts._numDocs);
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
Test::requireThatWeCanReadPostingList()
{
    TermFieldMatchDataArray mda;
    { // field 'f1'
        LookupResult::UP r = _index->lookup(0, "w1");
        PostingListHandle::UP h = _index->readPostingList(*r);
        SearchIterator * sb = h->createIterator(r->counts, mda);
        sb->initFullRange();
        EXPECT_EQUAL("1,3", toString(*sb));
        delete sb;
    }
}

void
Test::require_that_we_can_get_field_length_info()
{
    auto info = _index->get_field_length_info("f1");
    EXPECT_EQUAL(3.5, info.get_average_field_length());
    EXPECT_EQUAL(21u, info.get_num_samples());
    info = _index->get_field_length_info("f2");
    EXPECT_EQUAL(4.0, info.get_average_field_length());
    EXPECT_EQUAL(23u, info.get_num_samples());
    info = _index->get_field_length_info("f3");
    EXPECT_EQUAL(0.0, info.get_average_field_length());
    EXPECT_EQUAL(0u, info.get_num_samples());
}

void
Test::requireThatWeCanReadBitVector()
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
Test::requireThatBlueprintIsCreated()
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
        EXPECT_EQUAL(2u, b->getState().estimate().estHits);
        EXPECT_TRUE(!b->getState().estimate().empty);
    }
    { // known field & word without hits
        Blueprint::UP b =
            _index->createBlueprint(_requestContext, FieldSpec("f1", 0, 0), makeTerm("w2"));
//        std::cerr << "BP = " << typeid(*b).name() << std::endl;
        EXPECT_TRUE((dynamic_cast<DiskTermBlueprint *>(b.get()) != NULL) ||
                    (dynamic_cast<EmptyBlueprint *>(b.get()) != NULL));
        EXPECT_EQUAL(0u, b->getState().estimate().estHits);
        EXPECT_TRUE(b->getState().estimate().empty);
    }
}

void
Test::requireThatBlueprintCanCreateSearchIterators()
{
    TermFieldMatchData md;
    TermFieldMatchDataArray mda;
    mda.add(&md);
    Blueprint::UP b;
    SearchIterator::UP s;
    { // bit vector due to isFilter
        b = _index->createBlueprint(_requestContext, FieldSpec("f2", 0, 0, true), makeTerm("w2"));
        b->fetchPostings(true);
        s = (dynamic_cast<LeafBlueprint *>(b.get()))->createLeafSearch(mda, true);
        EXPECT_TRUE(dynamic_cast<BitVectorIterator *>(s.get()) != NULL);
    }
    { // bit vector due to no ranking needed
        b = _index->createBlueprint(_requestContext, FieldSpec("f2", 0, 0, false), makeTerm("w2"));
        b->fetchPostings(true);
        s = (dynamic_cast<LeafBlueprint *>(b.get()))->createLeafSearch(mda, true);
        EXPECT_FALSE(dynamic_cast<BitVectorIterator *>(s.get()) != NULL);
        TermFieldMatchData md2;
        md2.tagAsNotNeeded();
        TermFieldMatchDataArray mda2;
        mda2.add(&md2);
        EXPECT_TRUE(mda2[0]->isNotNeeded());
        s = (dynamic_cast<LeafBlueprint *>(b.get()))->createLeafSearch(mda2, false);
        EXPECT_TRUE(dynamic_cast<BitVectorIterator *>(s.get()) != NULL);
    }
    { // fake bit vector
        b = _index->createBlueprint(_requestContext, FieldSpec("f1", 0, 0, true), makeTerm("w2"));
//        std::cerr << "BP = " << typeid(*b).name() << std::endl;
        b->fetchPostings(true);
        s = (dynamic_cast<LeafBlueprint *>(b.get()))->createLeafSearch(mda, true);
//        std::cerr << "SI = " << typeid(*s).name() << std::endl;
        EXPECT_TRUE((dynamic_cast<BooleanMatchIteratorWrapper *>(s.get()) != NULL) ||
                    dynamic_cast<EmptySearch *>(s.get()));
    }
    { // posting list iterator
        b = _index->createBlueprint(_requestContext, FieldSpec("f1", 0, 0), makeTerm("w1"));
        b->fetchPostings(true);
        s = (dynamic_cast<LeafBlueprint *>(b.get()))->createLeafSearch(mda, true);
        ASSERT_TRUE((dynamic_cast<ZcRareWordPosOccIterator<true, false> *>(s.get()) != NULL));
    }
}

Test::Test() :
    TestDiskIndex()
{
}

Test::~Test() {}

int
Test::Main()
{
    TEST_INIT("diskindex_test");

    if (_argc > 0) {
        DummyFileHeaderContext::setCreator(_argv[0]);
    }

    vespalib::mkdir("index", false);
    TEST_DO(openIndex("index/1fedewe", false, false, true, true, true));
    TEST_DO(requireThatLookupIsWorking(true, true, true));
    TEST_DO(openIndex("index/1fede", false, false, true, true, false));
    TEST_DO(requireThatLookupIsWorking(true, true, false));
    TEST_DO(openIndex("index/1fewe", false, false, true, false, true));
    TEST_DO(requireThatLookupIsWorking(true, false, true));
    TEST_DO(openIndex("index/1fe", false, false, true, false, false));
    TEST_DO(requireThatLookupIsWorking(true, false, false));
    buildSchema();
    TEST_DO(openIndex("index/1dewe", false, false, false, true, true));
    TEST_DO(requireThatLookupIsWorking(false, true, true));
    TEST_DO(openIndex("index/1de", false, false, false, true, false));
    TEST_DO(requireThatLookupIsWorking(false, true, false));
    TEST_DO(openIndex("index/1we", false, false, false, false, true));
    TEST_DO(requireThatLookupIsWorking(false, false, true));
    TEST_DO(openIndex("index/1", false, false, false, false, false));
    TEST_DO(requireThatLookupIsWorking(false, false, false));
    TEST_DO(requireThatWeCanReadPostingList());
    TEST_DO(require_that_we_can_get_field_length_info());
    TEST_DO(requireThatWeCanReadBitVector());
    TEST_DO(requireThatBlueprintIsCreated());
    TEST_DO(requireThatBlueprintCanCreateSearchIterators());

    TEST_DO(openIndex("index/2", true, false, false, false, false));
    TEST_DO(requireThatLookupIsWorking(false, false, false));
    TEST_DO(requireThatWeCanReadPostingList());
    TEST_DO(require_that_we_can_get_field_length_info());
    TEST_DO(requireThatWeCanReadBitVector());
    TEST_DO(requireThatBlueprintIsCreated());
    TEST_DO(requireThatBlueprintCanCreateSearchIterators());

    TEST_DO(openIndex("index/3", false, true, false, false, false));
    TEST_DO(requireThatLookupIsWorking(false, false, false));
    TEST_DO(requireThatWeCanReadPostingList());
    TEST_DO(require_that_we_can_get_field_length_info());
    TEST_DO(requireThatWeCanReadBitVector());
    TEST_DO(requireThatBlueprintIsCreated());
    TEST_DO(requireThatBlueprintCanCreateSearchIterators());

    TEST_DO(openIndex("index/4", true, true, false, false, false));
    TEST_DO(requireThatLookupIsWorking(false, false, false));
    TEST_DO(requireThatWeCanReadPostingList());
    TEST_DO(require_that_we_can_get_field_length_info());
    TEST_DO(requireThatWeCanReadBitVector());
    TEST_DO(requireThatBlueprintIsCreated());
    TEST_DO(requireThatBlueprintCanCreateSearchIterators());
    TEST_DO(requireThatSearchIteratorsConforms());

    TEST_DONE();
}

}
}

TEST_APPHOOK(search::diskindex::Test);
