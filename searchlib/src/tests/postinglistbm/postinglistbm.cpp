// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "andstress.h"
#include <vespa/fastos/app.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/common/resultset.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/test/fakedata/fake_match_loop.h>
#include <vespa/searchlib/test/fakedata/fakeposting.h>
#include <vespa/searchlib/test/fakedata/fakeword.h>
#include <vespa/searchlib/test/fakedata/fakewordset.h>
#include <vespa/searchlib/test/fakedata/fpfactory.h>
#include <vespa/searchlib/util/rand48.h>

#include <vespa/log/log.h>

using search::ResultSet;
using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataArray;
using search::index::schema::CollectionType;
using search::index::schema::DataType;
using search::queryeval::SearchIterator;

using namespace search::index;
using namespace search::fakedata;

// needed to resolve external symbol from httpd.h on AIX
void FastS_block_usr2() {}


namespace postinglistbm {

class PostingListBM : public FastOS_Application {
private:
    bool _verbose;
    uint32_t _numDocs;
    uint32_t _commonDocFreq;
    uint32_t _numWordsPerClass;
    std::vector<std::string> _postingTypes;
    uint32_t _loops;
    unsigned int _skipCommonPairsRate;
    FakeWordSet _wordSet;
    uint32_t _stride;
    bool _unpack;

public:
    search::Rand48 _rnd;

private:
    void usage();
    void badPostingType(const std::string &postingType);
    void testFake(const std::string &postingType,
                  const Schema &schema,
                  const FakeWord &word);
public:
    PostingListBM();
    ~PostingListBM();
    int Main() override;
};

void
PostingListBM::usage()
{
    printf("Usage: postinglistbm "
           "[-C <skipCommonPairsRate>] "
           "[-T {string, array, weightedSet}] "
           "[-a] "
           "[-c <commonDoqFreq>] "
           "[-d <numDocs>] "
           "[-l <numLoops>] "
           "[-s <stride>] "
           "[-t <postingType>] "
           "[-u] "
           "[-v] "
           "[-w <numWordsPerClass>] "
           "[-q]\n");
}

void
PostingListBM::badPostingType(const std::string &postingType)
{
    printf("Bad posting list type: %s\n", postingType.c_str());
    printf("Supported types: ");

    bool first = true;
    for (const auto& type : getPostingTypes()) {
        if (first) {
            first = false;
        } else {
            printf(", ");
        }
        printf("%s", type.c_str());
    }
    printf("\n");
}

PostingListBM::PostingListBM()
    : _verbose(false),
      _numDocs(10000000),
      _commonDocFreq(50000),
      _numWordsPerClass(100),
      _postingTypes(),
      _loops(1),
      _skipCommonPairsRate(1),
      _wordSet(),
      _stride(0),
      _unpack(false),
      _rnd()
{
}

PostingListBM::~PostingListBM() = default;

void
validate_posting_for_word(const FakePosting& posting, const FakeWord& word, bool verbose)
{
    TermFieldMatchData md;
    TermFieldMatchDataArray tfmda;
    tfmda.add(&md);

    std::unique_ptr<SearchIterator> iterator(posting.createIterator(tfmda));
    if (posting.hasWordPositions()) {
        word.validate(iterator.get(), tfmda, verbose);
    } else {
        word.validate(iterator.get(), verbose);
    }
}

void
PostingListBM::testFake(const std::string &postingType,
                        const Schema &schema,
                        const FakeWord &word)
{
    auto posting_factory = getFPFactory(postingType, schema);
    std::vector<const FakeWord *> words;
    words.push_back(&word);
    posting_factory->setup(words);
    auto posting = posting_factory->make(word);

    printf("%s.bitsize=%d+%d+%d+%d+%d\n",
           posting->getName().c_str(),
           static_cast<int>(posting->bitSize()),
           static_cast<int>(posting->l1SkipBitSize()),
           static_cast<int>(posting->l2SkipBitSize()),
           static_cast<int>(posting->l3SkipBitSize()),
           static_cast<int>(posting->l4SkipBitSize()));

    validate_posting_for_word(*posting, word, _verbose);

    uint64_t scanTime = 0;
    uint64_t scanUnpackTime = 0;
    int hits1 = FakeMatchLoop::single_posting_scan(*posting, word.getDocIdLimit(), scanTime);
    int hits2 = FakeMatchLoop::single_posting_scan_with_unpack(*posting, word.getDocIdLimit(), scanUnpackTime);

    printf("testFake '%s' hits1=%d, hits2=%d, scanTime=%" PRIu64
           ", scanUnpackTime=%" PRIu64 "\n",
           posting->getName().c_str(),
           hits1, hits2, scanTime, scanUnpackTime);
}

void
testFakePair(const std::string &postingType,
             const Schema &schema,
             bool unpack,
             const FakeWord &fw1, const FakeWord &fw2)
{
    std::unique_ptr<FPFactory> ff(getFPFactory(postingType, schema));
    std::vector<const FakeWord *> v;
    v.push_back(&fw1);
    v.push_back(&fw2);
    ff->setup(v);
    FakePosting::SP f1(ff->make(fw1));
    FakePosting::SP f2(ff->make(fw2));

    uint64_t scanUnpackTime = 0;
    int hits = unpack ?
               FakeMatchLoop::and_pair_posting_scan_with_unpack(*f1, *f2, fw1.getDocIdLimit(), scanUnpackTime) :
               FakeMatchLoop::and_pair_posting_scan(*f1, *f2, fw1.getDocIdLimit(), scanUnpackTime);
    printf("Fakepair %s AND %s => %d hits, %" PRIu64 " cycles\n",
           f1->getName().c_str(),
           f2->getName().c_str(),
           hits,
           scanUnpackTime);
}

int
PostingListBM::Main()
{
    int argi;
    char c;
    const char *optArg;
    bool doandstress;

    doandstress = false;
    argi = 1;
    bool hasElements = false;
    bool hasElementWeights = false;
    bool quick = false;

    while ((c = GetOpt("C:ac:d:l:s:t:uvw:T:q", optArg, argi)) != -1) {
        switch(c) {
        case 'C':
            _skipCommonPairsRate = atoi(optArg);
            break;
        case 'T':
            if (strcmp(optArg, "single") == 0) {
                hasElements = false;
                hasElementWeights = false;
            } else if (strcmp(optArg, "array") == 0) {
                hasElements = true;
                hasElementWeights = false;
            } else if (strcmp(optArg, "weightedSet") == 0) {
                hasElements = true;
                hasElementWeights = true;
            } else {
                printf("Bad collection type: %s\n", optArg);
                return 1;
            }
            break;
        case 'a':
            doandstress = true;
            break;
        case 'c':
            _commonDocFreq = atoi(optArg);
            break;
        case 'd':
            _numDocs = atoi(optArg);
            break;
        case 'l':
            _loops = atoi(optArg);
            break;
        case 's':
            _stride = atoi(optArg);
            break;
        case 't':
            do {
                Schema schema;
                Schema::IndexField indexField("field0",
                        DataType::STRING,
                        CollectionType::SINGLE);
                schema.addIndexField(indexField);
                std::unique_ptr<FPFactory> ff(getFPFactory(optArg, schema));
                if (ff.get() == nullptr) {
                    badPostingType(optArg);
                    return 1;
                }
            } while (0);
            _postingTypes.push_back(optArg);
            break;
        case 'u':
            _unpack = true;
            break;
        case 'v':
            _verbose = true;
            break;
        case 'w':
            _numWordsPerClass = atoi(optArg);
            break;
        case 'q':
            quick = true;
            _numDocs = 36000;
            _commonDocFreq = 10000;
            _numWordsPerClass = 5;
            break;
        default:
            usage();
            return 1;
        }
    }

    if (_commonDocFreq > _numDocs) {
        usage();
        return 1;
    }

    _wordSet.setupParams(hasElements, hasElementWeights);

    uint32_t w1dfreq = 10;
    uint32_t w4dfreq = 790000;
    uint32_t w5dfreq = 290000;
    uint32_t w4w5od = 100000;
    uint32_t numTasks = 40000;
    if (quick) {
        w1dfreq = 2;
        w4dfreq = 19000;
        w5dfreq = 5000;
        w4w5od = 1000;
        numTasks = 40;
    }
    

    FakeWord word1(_numDocs, w1dfreq, w1dfreq / 2, "word1", _rnd,
                   _wordSet.getFieldsParams(), _wordSet.getPackedIndex());
    FakeWord word2(_numDocs, 1000, 500, "word2", word1, 4, _rnd,
                   _wordSet.getFieldsParams(), _wordSet.getPackedIndex());
    FakeWord word3(_numDocs, _commonDocFreq, _commonDocFreq / 2,
                   "word3", word1, 10, _rnd,
                   _wordSet.getFieldsParams(), _wordSet.getPackedIndex());
    FakeWord word4(_numDocs, w4dfreq, w4dfreq / 2,
                   "word4", _rnd,
                   _wordSet.getFieldsParams(), _wordSet.getPackedIndex());
    FakeWord word5(_numDocs, w5dfreq, w5dfreq / 2,
                   "word5", word4, w4w5od, _rnd,
                   _wordSet.getFieldsParams(), _wordSet.getPackedIndex());

    if (_postingTypes.empty()) {
        _postingTypes = getPostingTypes();
    }

    for (const auto& type : _postingTypes) {
        testFake(type, _wordSet.getSchema(), word1);
        testFake(type, _wordSet.getSchema(), word2);
        testFake(type, _wordSet.getSchema(), word3);
    }

    for (const auto& type : _postingTypes) {
        testFakePair(type, _wordSet.getSchema(), false, word1, word3);
        testFakePair(type, _wordSet.getSchema(), false, word2, word3);
    }

    for (const auto& type : _postingTypes) {
        testFakePair(type, _wordSet.getSchema(), false, word4, word5);
    }

    if (doandstress) {
        _wordSet.setupWords(_rnd, _numDocs, _commonDocFreq, _numWordsPerClass);
    }
    if (doandstress) {
        AndStress andstress;
        andstress.run(_rnd, _wordSet,
                      _numDocs, _commonDocFreq, _postingTypes, _loops,
                      _skipCommonPairsRate,
                      numTasks,
                      _stride,
                      _unpack);
    }
    return 0;
}

}

int
main(int argc, char **argv)
{
    postinglistbm::PostingListBM app;

    setvbuf(stdout, nullptr, _IOLBF, 32768);
    app._rnd.srand48(32);
    return app.Entry(argc, argv);
}
