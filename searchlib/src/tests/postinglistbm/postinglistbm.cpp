// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stress_runner.h"
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/common/resultset.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/test/fakedata/fake_match_loop.h>
#include <vespa/searchlib/test/fakedata/fakeposting.h>
#include <vespa/searchlib/test/fakedata/fakeword.h>
#include <vespa/searchlib/test/fakedata/fakewordset.h>
#include <vespa/searchlib/test/fakedata/fpfactory.h>
#include <vespa/vespalib/util/rand48.h>
#include <vespa/vespalib/util/size_literals.h>
#include <unistd.h>

#include <vespa/log/log.h>

using search::ResultSet;
using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataArray;
using search::index::schema::CollectionType;
using search::index::schema::DataType;
using search::queryeval::SearchIterator;

using namespace search::index;
using namespace search::fakedata;

namespace postinglistbm {

class PostingListBM {
private:
    uint32_t _numDocs;
    uint32_t _commonDocFreq;
    uint32_t _mediumDocFreq;
    uint32_t _rareDocFreq;
    uint32_t _numWordsPerClass;
    std::vector<std::string> _postingTypes;
    StressRunner::OperatorType _operatorType;
    uint32_t _loops;
    uint32_t _skipCommonPairsRate;
    FakeWordSet _wordSet;
    uint32_t _stride;
    bool _unpack;

public:
    vespalib::Rand48 _rnd;

public:
    PostingListBM();
    ~PostingListBM();
    int main(int argc, char **argv);
};

void
usage()
{
    printf("Usage: postinglistbm "
           "[-C <skipCommonPairsRate>] "
           "[-T {string, array, weightedSet}] "
           "[-c <commonDoqFreq>] "
           "[-m <mediumDoqFreq>] "
           "[-r <rareDoqFreq>] "
           "[-d <numDocs>] "
           "[-l <numLoops>] "
           "[-s <stride>] "
           "[-t <postingType>] "
           "[-o {direct, and, or}] "
           "[-u] "
           "[-w <numWordsPerClass>]\n");
}

void
badPostingType(const std::string &postingType)
{
    printf("Bad posting list type: '%s'\n", postingType.c_str());
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
    : _numDocs(10000000),
      _commonDocFreq(50000),
      _mediumDocFreq(1000),
      _rareDocFreq(10),
      _numWordsPerClass(100),
      _postingTypes(),
      _operatorType(StressRunner::OperatorType::And),
      _loops(1),
      _skipCommonPairsRate(1),
      _wordSet(),
      _stride(0),
      _unpack(false),
      _rnd()
{
}

PostingListBM::~PostingListBM() = default;

int
PostingListBM::main(int argc, char **argv)
{
    int c;

    bool hasElements = false;
    bool hasElementWeights = false;

    while ((c = getopt(argc, argv, "C:c:m:r:d:l:s:t:o:uw:T:q")) != -1) {
        switch(c) {
        case 'C':
            _skipCommonPairsRate = atoi(optarg);
            break;
        case 'T':
            if (strcmp(optarg, "single") == 0) {
                hasElements = false;
                hasElementWeights = false;
            } else if (strcmp(optarg, "array") == 0) {
                hasElements = true;
                hasElementWeights = false;
            } else if (strcmp(optarg, "weightedSet") == 0) {
                hasElements = true;
                hasElementWeights = true;
            } else {
                printf("Bad collection type: '%s'\n", optarg);
                printf("Supported types: single, array, weightedSet\n");
                return 1;
            }
            break;
        case 'c':
            _commonDocFreq = atoi(optarg);
            break;
        case 'm':
            _mediumDocFreq = atoi(optarg);
            break;
        case 'r':
            _rareDocFreq = atoi(optarg);
            break;
        case 'd':
            _numDocs = atoi(optarg);
            break;
        case 'l':
            _loops = atoi(optarg);
            break;
        case 's':
            _stride = atoi(optarg);
            break;
        case 't':
            do {
                Schema schema;
                Schema::IndexField indexField("field0",
                        DataType::STRING,
                        CollectionType::SINGLE);
                schema.addIndexField(indexField);
                std::unique_ptr<FPFactory> ff(getFPFactory(optarg, schema));
                if (ff.get() == nullptr) {
                    badPostingType(optarg);
                    return 1;
                }
            } while (0);
            _postingTypes.push_back(optarg);
            break;
        case 'o':
        {
           vespalib::string operatorType(optarg);
           if (operatorType == "direct") {
               _operatorType = StressRunner::OperatorType::Direct;
           } else if (operatorType == "and") {
               _operatorType = StressRunner::OperatorType::And;
           } else if (operatorType == "or") {
               _operatorType = StressRunner::OperatorType::Or;
           } else {
               printf("Bad operator type: '%s'\n", operatorType.c_str());
               printf("Supported types: direct, and, or\n");
               return 1;
           }
           break;
        }
        case 'u':
            _unpack = true;
            break;
        case 'w':
            _numWordsPerClass = atoi(optarg);
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

    uint32_t numTasks = 40000;

    if (_postingTypes.empty()) {
        _postingTypes = getPostingTypes();
    }

    _wordSet.setupWords(_rnd, _numDocs, _commonDocFreq, _mediumDocFreq, _rareDocFreq, _numWordsPerClass);

    StressRunner::run(_rnd,
                      _wordSet,
                      _postingTypes,
                      _operatorType,
                      _loops,
                      _skipCommonPairsRate,
                      numTasks,
                      _stride,
                      _unpack);
    return 0;
}

}

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    postinglistbm::PostingListBM app;

    setvbuf(stdout, nullptr, _IOLBF, 32_Ki);
    app._rnd.srand48(32);
    return app.main(argc, argv);
}
