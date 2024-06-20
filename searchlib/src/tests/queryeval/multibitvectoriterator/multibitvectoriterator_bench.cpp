// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/queryeval/multibitvectoriterator.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/queryeval/andsearch.h>
#include <vespa/searchlib/queryeval/orsearch.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/log/log.h>
LOG_SETUP("multibitvectoriterator_test");

using namespace search::queryeval;
using namespace search::fef;
using namespace search;

//-----------------------------------------------------------------------------

struct Fixture
{
    void benchmark();
    template <typename T>
    void testSearch(bool strict);
    void searchAndCompare(SearchIterator::UP s, uint32_t docIdLimit);
    void setup();
    std::vector< BitVector::UP > _bvs;
    uint32_t         _numSearch;
    uint32_t         _numDocs;
    bool             _strict;
    bool             _optimize;
    vespalib::string _type;
    std::vector<int> _fillLimits;

    Fixture(int argc, char **argv) {
        _type = argv[1];
        _strict = vespalib::string(argv[2]) == vespalib::string("strict");
        _optimize = vespalib::string(argv[3]) == vespalib::string("optimize");
        _numSearch = strtoul(argv[4], NULL, 0);
        _numDocs = strtoul(argv[5], NULL, 0);
        for (int i(6); i < argc; i++) {
            _fillLimits.push_back((RAND_MAX/100) * strtoul(argv[i], NULL, 0));
        }
    }
    ~Fixture();
};
Fixture::~Fixture() = default;

void Fixture::setup()
{
    for(size_t i(0); i < _fillLimits.size(); i++) {
        _bvs.push_back(BitVector::create(_numDocs));
        BitVector & bv(*_bvs.back());
        for (size_t j(0); j < bv.size(); j++) {
            int r = rand();
            if (r < _fillLimits[i]) {
                bv.setBit(j);
            }
        }
        bv.invalidateCachedCount();
        LOG(info, "Filled bitvector %ld with %d bits", i, bv.countTrueBits());
    }
}

using H = std::vector<uint32_t>;

H
seek(SearchIterator & s, uint32_t docIdLimit)
{
    H h;
    for (uint32_t docId(0); docId < docIdLimit; ) {
        if (s.seek(docId)) {
            h.push_back(docId);
            docId++;
        } else {
            if (s.getDocId() > docId) {
                docId = s.getDocId();
            } else {
                docId++;
            }
        }
        //printf("docId = %u\n", docId);
    }
    return h;
}

void
Fixture::benchmark()
{
    if (_type == "and") {
        LOG(info, "Testing 'and'");
        for (size_t i(0); i < _numSearch; i++) {
            testSearch<AndSearch>(_strict);
        }
    } else {
        LOG(info, "Testing 'or'");
        for (size_t i(0); i < _numSearch; i++) {
            testSearch<OrSearch>(_strict);
        }
    }
}

template <typename T>
void
Fixture::testSearch(bool strict)
{
    TermFieldMatchData tfmd;
    MultiSearch::Children andd;
    for (size_t i(0); i < _bvs.size(); i++) {
        andd.push_back(BitVectorIterator::create(_bvs[i].get(), tfmd, strict, false));
    }
    SearchIterator::UP s = T::create(std::move(andd), strict);
    if (_optimize) {
        LOG(info, "Optimizing iterator");
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
    }
    H h = seek(*s, _numDocs);
    LOG(info, "Found %ld hits", h.size());
}

TEST_MAIN() {
    if (argc < 6) {
        LOG(info, "%s <'and/or'> <'strict/no-strict'> <'optimize/no-optimize> <numsearch> <numdocs> <fill 1> [<fill N>]", argv[0]);
        exit(1);
    }
    Fixture fixture(argc, argv);
    LOG(info, "Start setup of '%s' isearch with %ld vectors with %d documents", fixture._type.c_str(), fixture._fillLimits.size(), fixture._numDocs);
    fixture.setup();
    LOG(info, "Start benchmark");
    fixture.benchmark();
    LOG(info, "Done benchmark");
}
