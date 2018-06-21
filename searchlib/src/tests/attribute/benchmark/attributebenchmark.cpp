// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/util/randomgenerator.h>
#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/singlenumericattribute.h>
#include <vespa/searchlib/attribute/multinumericattribute.h>
#include <vespa/searchlib/attribute/singlestringattribute.h>
#include <vespa/searchlib/attribute/multistringattribute.h>
#include <vespa/searchlib/attribute/attrvector.h>
#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/fastos/thread.h>
#include <vespa/fastos/app.h>
#include <iostream>
#include <fstream>
#include "attributesearcher.h"
#include "attributeupdater.h"
#include <sys/resource.h>

#include <vespa/log/log.h>
LOG_SETUP("attributebenchmark");

using std::shared_ptr;

typedef std::vector<uint32_t> NumVector;
typedef std::vector<vespalib::string> StringVector;
typedef AttributeVector::SP AttributePtr;
typedef AttributeVector::DocId DocId;
typedef search::attribute::Config AttrConfig;
using search::attribute::BasicType;
using search::attribute::CollectionType;

namespace search {

class AttributeBenchmark : public FastOS_Application
{
private:
    class Config {
    public:
        vespalib::string _attribute;
        uint32_t _numDocs;
        uint32_t _numUpdates;
        uint32_t _numValues;
        uint32_t _numSearchers;
        uint32_t _numQueries;
        bool _searchersOnly;
        bool _validate;
        uint32_t _populateRuns;
        uint32_t _updateRuns;
        uint32_t _commitFreq;
        uint32_t _minValueCount;
        uint32_t _maxValueCount;
        uint32_t _minStringLen;
        uint32_t _maxStringLen;
        uint32_t _seed;
        bool _writeAttribute;
        int64_t _rangeStart;
        int64_t _rangeEnd;
        int64_t _rangeDelta;
        bool _rangeSearch;
        uint32_t _prefixLength;
        bool _prefixSearch;


        Config() : _attribute(""), _numDocs(0), _numUpdates(0), _numValues(0),
        _numSearchers(0), _numQueries(0), _searchersOnly(true), _validate(false), _populateRuns(0), _updateRuns(0),
        _commitFreq(0), _minValueCount(0), _maxValueCount(0), _minStringLen(0), _maxStringLen(0), _seed(0),
        _writeAttribute(false), _rangeStart(0), _rangeEnd(0), _rangeDelta(0), _rangeSearch(false),
        _prefixLength(0), _prefixSearch(false) {}
        void printXML() const;
    };

    class Resource {
    private:
        std::vector<struct rusage> _usages;
        struct rusage _reset;

    public:
        Resource() : _usages(), _reset() { reset(); };
        void reset() {
            getrusage(0, &_reset);
        }
        void saveUsage() {
            struct rusage now;
            getrusage(0, &now);
            struct rusage usage = computeDifference(_reset, now);
            _usages.push_back(usage);
        }
        void printLastXML(uint32_t opCount) {
            (void) opCount;
            struct rusage & usage = _usages.back();
            std::cout << "<ru_utime>" << usage.ru_utime.tv_sec * 1000 + usage.ru_utime.tv_usec / 1000
                << "</ru_utime>" << std::endl;
            std::cout << "<ru_stime>" << usage.ru_stime.tv_sec * 1000 + usage.ru_stime.tv_usec / 1000
                << "</ru_stime>" << std::endl;
            std::cout << "<ru_nvcsw>" << usage.ru_nvcsw << "</ru_nvcsw>" << std::endl;
            std::cout << "<ru_nivcsw>" << usage.ru_nivcsw << "</ru_nivcsw>" << std::endl;
        }
        static struct rusage computeDifference(struct rusage & first, struct rusage & second);
    };

    FastOS_ThreadPool * _threadPool;
    Config _config;
    RandomGenerator _rndGen;

    void init(const Config & config);
    void usage();

    // benchmark helper methods
    void addDocs(const AttributePtr & ptr, uint32_t numDocs);
    template <typename Vector, typename T, typename BT>
    void benchmarkPopulate(const AttributePtr & ptr, const std::vector<T> & values, uint32_t id);
    template <typename Vector, typename T, typename BT>
    void benchmarkUpdate(const AttributePtr & ptr, const std::vector<T> & values, uint32_t id);

    template <typename T>
    std::vector<vespalib::string> prepareForPrefixSearch(const std::vector<T> & values) const;
    template <typename T>
    void benchmarkSearch(const AttributePtr & ptr, const std::vector<T> & values);
    template <typename Vector, typename T, typename BT>
    void benchmarkSearchWithUpdater(const AttributePtr & ptr,
                                    const std::vector<T> & values);

    template <typename Vector, typename T, typename BT>
    void benchmarkAttribute(const AttributePtr & ptr, const std::vector<T> & values);

    // Numeric Attribute
    void benchmarkNumeric(const AttributePtr & ptr);

    // String Attribute
    void benchmarkString(const AttributePtr & ptr);


public:
    AttributeBenchmark() : _threadPool(NULL), _config(), _rndGen() {}
    ~AttributeBenchmark() {
        if (_threadPool != NULL) {
            delete _threadPool;
        }
    }
    int Main() override;
};


void
AttributeBenchmark::Config::printXML() const
{
    std::cout << "<config>" << std::endl;
    std::cout << "<attribute>" << _attribute << "</attribute>" << std::endl;
    std::cout << "<num-docs>" << _numDocs << "</num-docs>" << std::endl;
    std::cout << "<num-updates>" << _numUpdates << "</num-updates>" << std::endl;
    std::cout << "<num-values>" << _numValues << "</num-values>" << std::endl;
    std::cout << "<num-searchers>" << _numSearchers << "</num-searchers>" << std::endl;
    std::cout << "<num-queries>" << _numQueries << "</num-queries>" << std::endl;
    std::cout << "<searchers-only>" << (_searchersOnly ? "true" : "false") << "</searchers-only>" << std::endl;
    std::cout << "<validate>" << (_validate ? "true" : "false") << "</validate>" << std::endl;
    std::cout << "<populate-runs>" << _populateRuns << "</populate-runs>" << std::endl;
    std::cout << "<update-runs>" << _updateRuns << "</update-runs>" << std::endl;
    std::cout << "<commit-freq>" << _commitFreq << "</commit-freq>" << std::endl;
    std::cout << "<min-value-count>" << _minValueCount << "</min-value-count>" << std::endl;
    std::cout << "<max-value-count>" << _maxValueCount << "</max-value-count>" << std::endl;
    std::cout << "<min-string-len>" << _minStringLen << "</min-string-len>" << std::endl;
    std::cout << "<max-string-len>" << _maxStringLen << "</max-string-len>" << std::endl;
    std::cout << "<seed>" << _seed << "</seed>" << std::endl;
    std::cout << "<range-start>" << _rangeStart << "</range-start>" << std::endl;
    std::cout << "<range-end>" << _rangeEnd << "</range-end>" << std::endl;
    std::cout << "<range-delta>" << _rangeDelta << "</range-delta>" << std::endl;
    std::cout << "<range-search>" << (_rangeSearch ? "true" : "false") << "</range-search>" << std::endl;
    std::cout << "<prefix-length>" << _prefixLength << "</range-length>" << std::endl;
    std::cout << "<prefix-search>" << (_prefixSearch ? "true" : "false") << "</prefix-search>" << std::endl;
    std::cout << "</config>" << std::endl;
}

void
AttributeBenchmark::init(const Config & config)
{
    _config = config;
    _rndGen.srand(_config._seed);
}


//-----------------------------------------------------------------------------
// Benchmark helper methods
//-----------------------------------------------------------------------------
void
AttributeBenchmark::addDocs(const AttributePtr & ptr, uint32_t numDocs)
{
    DocId startDoc;
    DocId lastDoc;
    bool success = ptr->addDocs(startDoc, lastDoc, numDocs);
    assert(success);
    (void) success;
    assert(startDoc == 0);
    assert(lastDoc + 1 == numDocs);
    assert(ptr->getNumDocs() == numDocs);
}

template <typename Vector, typename T, typename BT>
void
AttributeBenchmark::benchmarkPopulate(const AttributePtr & ptr, const std::vector<T> & values, uint32_t id)
{
    std::cout << "<!-- Populate " << _config._numDocs << " documents -->" << std::endl;
    AttributeUpdater<Vector, T, BT>
        updater(ptr, values, _rndGen, _config._validate, _config._commitFreq,
                _config._minValueCount, _config._maxValueCount);
    updater.populate();
    std::cout << "<populate id='" << id << "'>" << std::endl;
    updater.getStatus().printXML();
    std::cout << "</populate>" << std::endl;
    if (_config._validate) {
        std::cout << "<!-- All " << updater.getValidator().getTotalCnt()
            << " asserts passed -->" << std::endl;
    }
}

template <typename Vector, typename T, typename BT>
void
AttributeBenchmark::benchmarkUpdate(const AttributePtr & ptr, const std::vector<T> & values, uint32_t id)
{
    std::cout << "<!-- Apply " << _config._numUpdates << " updates -->" << std::endl;
    AttributeUpdater<Vector, T, BT>
        updater(ptr, values, _rndGen, _config._validate, _config._commitFreq,
                _config._minValueCount, _config._maxValueCount);
    updater.update(_config._numUpdates);
    std::cout << "<update id='" << id << "'>" << std::endl;
    updater.getStatus().printXML();
    std::cout << "</update>" << std::endl;
    if (_config._validate) {
        std::cout << "<!-- All " << updater.getValidator().getTotalCnt()
            << " asserts passed -->" << std::endl;
    }
}

template <typename T>
std::vector<vespalib::string>
AttributeBenchmark::prepareForPrefixSearch(const std::vector<T> & values) const
{
    (void) values;
    return std::vector<vespalib::string>();
}

template <>
std::vector<vespalib::string>
AttributeBenchmark::prepareForPrefixSearch(const std::vector<AttributeVector::WeightedString> & values) const
{
    std::vector<vespalib::string> retval;
    retval.reserve(values.size());
    for (size_t i = 0; i < values.size(); ++i) {
        retval.push_back(values[i].getValue().substr(0, _config._prefixLength));
    }
    return retval;
}

template <typename T>
void
AttributeBenchmark::benchmarkSearch(const AttributePtr & ptr, const std::vector<T> & values)
{
    std::vector<AttributeSearcher *> searchers;
    if (_config._numSearchers > 0) {
        std::cout << "<!-- Starting " << _config._numSearchers << " searcher threads with "
            << _config._numQueries << " queries each -->" << std::endl;

        std::vector<vespalib::string> prefixStrings = prepareForPrefixSearch(values);

        for (uint32_t i = 0; i < _config._numSearchers; ++i) {
            if (_config._rangeSearch) {
                RangeSpec spec(_config._rangeStart, _config._rangeEnd, _config._rangeDelta);
                searchers.push_back(new AttributeRangeSearcher(ptr, spec, _config._numQueries));
            } else if (_config._prefixSearch) {
                searchers.push_back(new AttributePrefixSearcher(ptr, prefixStrings, _config._numQueries));
            } else {
                searchers.push_back(new AttributeFindSearcher<T>(ptr, values, _config._numQueries));
            }
            _threadPool->NewThread(searchers.back());
        }

        for (uint32_t i = 0; i < searchers.size(); ++i) {
            searchers[i]->join();
        }

        AttributeSearcherStatus totalStatus;
        for (uint32_t i = 0; i < searchers.size(); ++i) {
            std::cout << "<searcher-summary id='" << i << "'>" << std::endl;
            searchers[i]->getStatus().printXML();
            std::cout << "</searcher-summary>" << std::endl;
            totalStatus.merge(searchers[i]->getStatus());
            delete searchers[i];
        }
        std::cout << "<total-searcher-summary>" << std::endl;
        totalStatus.printXML();
        std::cout << "</total-searcher-summary>" << std::endl;
    }
}

template <typename Vector, typename T, typename BT>
void
AttributeBenchmark::benchmarkSearchWithUpdater(const AttributePtr & ptr,
                                               const std::vector<T> & values)
{
    if (_config._numSearchers > 0) {
        std::cout << "<!-- Starting 1 updater thread -->" << std::endl;
        AttributeUpdaterThread<Vector, T, BT>
            updater(ptr, values, _rndGen, _config._validate, _config._commitFreq,
                    _config._minValueCount, _config._maxValueCount);
        _threadPool->NewThread(&updater);
        benchmarkSearch(ptr, values);
        updater.stop();
        updater.join();
        std::cout << "<updater-summary>" << std::endl;
        updater.getStatus().printXML();
        std::cout << "</updater-summary>" << std::endl;
        if (_config._validate) {
            std::cout << "<!-- All " << updater.getValidator().getTotalCnt()
                << " asserts passed -->" << std::endl;
        }
    }
}

template <typename Vector, typename T, typename BT>
void
AttributeBenchmark::benchmarkAttribute(const AttributePtr & ptr, const std::vector<T> & values)
{
    addDocs(ptr, _config._numDocs);

    // populate
    for (uint32_t i = 0; i < _config._populateRuns; ++i) {
        benchmarkPopulate<Vector, T, BT>(ptr, values, i);
    }

    // update
    if (_config._numUpdates > 0) {
        for (uint32_t i = 0; i < _config._updateRuns; ++i) {
            benchmarkUpdate<Vector, T, BT>(ptr, values, i);
        }
    }

    // search
    if (_config._searchersOnly) {
        benchmarkSearch(ptr, values);
    } else {
        benchmarkSearchWithUpdater<Vector, T, BT>(ptr, values);
    }

    _threadPool->Close();
}


//-----------------------------------------------------------------------------
// Numeric Attribute
//-----------------------------------------------------------------------------
void
AttributeBenchmark::benchmarkNumeric(const AttributePtr & ptr)
{
    NumVector values;
    if (_config._rangeSearch) {
        values.reserve(_config._numValues);
        for (uint32_t i = 0; i < _config._numValues; ++i) {
            values.push_back(i);
        }
    } else {
        _rndGen.fillRandomIntegers(values, _config._numValues);
    }

    std::vector<int32_t> weights;
    _rndGen.fillRandomIntegers(weights, _config._numValues);

    std::vector<AttributeVector::WeightedInt> weightedVector;
    weightedVector.reserve(values.size());
    for (size_t i = 0; i < values.size(); ++i) {
        if (!ptr->hasWeightedSetType()) {
            weightedVector.push_back(AttributeVector::WeightedInt(values[i]));
        } else {
            weightedVector.push_back(AttributeVector::WeightedInt(values[i], weights[i]));
        }
    }
    benchmarkAttribute<IntegerAttribute, AttributeVector::WeightedInt, AttributeVector::WeightedInt>
        (ptr, weightedVector);
}


//-----------------------------------------------------------------------------
// String Attribute
//-----------------------------------------------------------------------------
void
AttributeBenchmark::benchmarkString(const AttributePtr & ptr)
{
    StringVector strings;
    _rndGen.fillRandomStrings(strings, _config._numValues, _config._minStringLen, _config._maxStringLen);

    std::vector<int32_t> weights;
    _rndGen.fillRandomIntegers(weights, _config._numValues);

    std::vector<AttributeVector::WeightedString> weightedVector;
    weightedVector.reserve(strings.size());
    for (size_t i = 0; i < strings.size(); ++i) {
        if (!ptr->hasWeightedSetType()) {
            weightedVector.push_back(AttributeVector::WeightedString(strings[i]));
        } else {
            weightedVector.push_back(AttributeVector::WeightedString(strings[i], weights[i]));
        }
    }
    benchmarkAttribute<StringAttribute, AttributeVector::WeightedString, AttributeVector::WeightedString>
        (ptr, weightedVector);
}


//-----------------------------------------------------------------------------
// Resource utilization
//-----------------------------------------------------------------------------
struct rusage
AttributeBenchmark::Resource::computeDifference(struct rusage & first, struct rusage & second)
{
    struct rusage result;
    // utime
    uint64_t firstutime = first.ru_utime.tv_sec * 1000000 + first.ru_utime.tv_usec;
    uint64_t secondutime = second.ru_utime.tv_sec * 1000000 + second.ru_utime.tv_usec;
    uint64_t resultutime = secondutime - firstutime;
    result.ru_utime.tv_sec = resultutime / 1000000;
    result.ru_utime.tv_usec = resultutime % 1000000;

    // stime
    uint64_t firststime = first.ru_stime.tv_sec * 1000000 + first.ru_stime.tv_usec;
    uint64_t secondstime = second.ru_stime.tv_sec * 1000000 + second.ru_stime.tv_usec;
    uint64_t resultstime = secondstime - firststime;
    result.ru_stime.tv_sec = resultstime / 1000000;
    result.ru_stime.tv_usec = resultstime % 1000000;

    result.ru_maxrss = second.ru_maxrss; // - first.ru_maxrss;
    result.ru_ixrss = second.ru_ixrss; // - first.ru_ixrss;
    result.ru_idrss = second.ru_idrss; // - first.ru_idrss;
    result.ru_isrss = second.ru_isrss; // - first.ru_isrss;
    result.ru_minflt = second.ru_minflt - first.ru_minflt;
    result.ru_majflt = second.ru_majflt - first.ru_majflt;
    result.ru_nswap = second.ru_nswap - first.ru_nswap;
    result.ru_inblock = second.ru_inblock - first.ru_inblock;
    result.ru_oublock = second.ru_oublock - first.ru_oublock;
    result.ru_msgsnd = second.ru_msgsnd - first.ru_msgsnd;
    result.ru_msgrcv = second.ru_msgrcv - first.ru_msgrcv;
    result.ru_nsignals = second.ru_nsignals - first.ru_nsignals;
    result.ru_nvcsw = second.ru_nvcsw - first.ru_nvcsw;
    result.ru_nivcsw = second.ru_nivcsw - first.ru_nivcsw;

    return result;
}


void
AttributeBenchmark::usage()
{
    std::cout << "usage: attributebenchmark [-n numDocs] [-u numUpdates] [-v numValues]" << std::endl;
    std::cout << "                          [-s numSearchers] [-q numQueries] [-p populateRuns] [-r updateRuns]" << std::endl;
    std::cout << "                          [-c commitFrequency] [-l minValueCount] [-h maxValueCount]" << std::endl;
    std::cout << "                          [-i minStringLen] [-a maxStringLen] [-e seed]" << std::endl;
    std::cout << "                          [-S rangeStart] [-E rangeEnd] [-D rangeDelta] [-L prefixLength]" << std::endl;
    std::cout << "                          [-b (searchers with updater)] [-R (range search)] [-P (prefix search)]" << std::endl;
    std::cout << "                          [-t (validate updates)] [-w (write attribute to disk)]" << std::endl;
    std::cout << "                          <attribute>" << std::endl;
    std::cout << " <attribute> : s-uint32, a-uint32, ws-uint32" << std::endl;
    std::cout << "               s-fa-uint32, a-fa-uint32, ws-fa-uint32" << std::endl;
    std::cout << "               s-fs-uint32, a-fs-uint32, ws-fs-uint32 ws-frs-uint32" << std::endl;
    std::cout << "               s-string, a-string, ws-string" << std::endl;
    std::cout << "               s-fs-string, a-fs-string, ws-fs-string ws-frs-string" << std::endl;
}

int
AttributeBenchmark::Main()
{
    Config dc;
    dc._numDocs = 50000;
    dc._numUpdates = 50000;
    dc._numValues = 1000;
    dc._numSearchers = 0;
    dc._numQueries = 1000;
    dc._searchersOnly = true;
    dc._validate = false;
    dc._populateRuns = 1;
    dc._updateRuns = 1;
    dc._commitFreq = 1000;
    dc._minValueCount = 0;
    dc._maxValueCount = 20;
    dc._minStringLen = 1;
    dc._maxStringLen = 50;
    dc._seed = 555;
    dc._writeAttribute = false;
    dc._rangeStart = 0;
    dc._rangeEnd = 1000;
    dc._rangeDelta = 10;
    dc._rangeSearch = false;
    dc._prefixLength = 2;
    dc._prefixSearch = false;

    int idx = 1;
    char opt;
    const char * arg;
    bool optError = false;
    while ((opt = GetOpt("n:u:v:s:q:p:r:c:l:h:i:a:e:S:E:D:L:bRPtw", arg, idx)) != -1) {
        switch (opt) {
        case 'n':
            dc._numDocs = atoi(arg);
            break;
        case 'u':
            dc._numUpdates = atoi(arg);
            break;
        case 'v':
            dc._numValues = atoi(arg);
            break;
        case 's':
            dc._numSearchers = atoi(arg);
            break;
        case 'q':
            dc._numQueries = atoi(arg);
            break;
        case 'p':
            dc._populateRuns = atoi(arg);
            break;
        case 'r':
            dc._updateRuns = atoi(arg);
            break;
        case 'c':
            dc._commitFreq = atoi(arg);
            break;
        case 'l':
            dc._minValueCount = atoi(arg);
            break;
        case 'h':
            dc._maxValueCount = atoi(arg);
            break;
        case 'i':
            dc._minStringLen = atoi(arg);
            break;
        case 'a':
            dc._maxStringLen = atoi(arg);
            break;
        case 'e':
            dc._seed = atoi(arg);
            break;
        case 'S':
            dc._rangeStart = strtoll(arg, NULL, 10);
            break;
        case 'E':
            dc._rangeEnd = strtoll(arg, NULL, 10);
            break;
        case 'D':
            dc._rangeDelta = strtoll(arg, NULL, 10);
            break;
        case 'L':
            dc._prefixLength = atoi(arg);
            break;
        case 'b':
            dc._searchersOnly = false;
            break;
        case 'R':
            dc._rangeSearch = true;
            break;
        case 'P':
            dc._prefixSearch = true;
            break;
        case 't':
            dc._validate = true;
            break;
        case 'w':
            dc._writeAttribute = true;
            break;
        default:
            optError = true;
            break;
        }
    }

    if (_argc != (idx + 1) || optError) {
        usage();
        return -1;
    }

    dc._attribute = vespalib::string(_argv[idx]);

    _threadPool = new FastOS_ThreadPool(256000);

    std::cout << "<attribute-benchmark>" << std::endl;
    init(dc);
    _config.printXML();

    AttributePtr ptr;

    if (_config._attribute == "s-int32") {
        std::cout << "<!-- Benchmark SingleValueNumericAttribute<int32_t> -->" << std::endl;
        ptr = AttributeFactory::createAttribute("s-int32", AttrConfig(BasicType::INT32, CollectionType::SINGLE));
        benchmarkNumeric(ptr);

    } else if (_config._attribute == "a-int32") {
        std::cout << "<!-- Benchmark MultiValueNumericAttribute<int32_t> (array) -->" << std::endl;
        ptr = AttributeFactory::createAttribute("a-int32", AttrConfig(BasicType::INT32, CollectionType::ARRAY));
        benchmarkNumeric(ptr);

    } else if (_config._attribute == "ws-int32") {
        std::cout << "<!-- Benchmark MultiValueNumericAttribute<int32_t> (wset) -->" << std::endl;
        ptr = AttributeFactory::createAttribute("ws-int32", AttrConfig(BasicType::INT32, CollectionType::WSET));
        benchmarkNumeric(ptr);

    } else if (_config._attribute == "s-fs-int32") {
        std::cout << "<!-- Benchmark SingleValueNumericPostingAttribute<int32_t> -->" << std::endl;
        AttrConfig cfg(BasicType::INT32, CollectionType::SINGLE);
        cfg.setFastSearch(true);
        ptr = AttributeFactory::createAttribute("s-fs-int32", cfg);
        benchmarkNumeric(ptr);

    } else if (_config._attribute == "a-fs-int32") {
        std::cout << "<!-- Benchmark MultiValueNumericPostingAttribute<int32_t> (array) -->" << std::endl;
        AttrConfig cfg(BasicType::INT32, CollectionType::ARRAY);
        cfg.setFastSearch(true);
        ptr = AttributeFactory::createAttribute("a-fs-int32", cfg);
        benchmarkNumeric(ptr);

    } else if (_config._attribute == "ws-fs-int32") {
        std::cout << "<!-- Benchmark MultiValueNumericPostingAttribute<int32_t> (wset) -->" << std::endl;
        AttrConfig cfg(BasicType::INT32, CollectionType::WSET);
        cfg.setFastSearch(true);
        ptr = AttributeFactory::createAttribute("ws-fs-int32", cfg);
        benchmarkNumeric(ptr);

    } else if (_config._attribute == "s-string") {
        std::cout << "<!-- Benchmark SingleValueStringAttribute -->" << std::endl;
        ptr = AttributeFactory::createAttribute("s-string", AttrConfig(BasicType::STRING, CollectionType::SINGLE));
        benchmarkString(ptr);

    } else if (_config._attribute == "a-string") {
        std::cout << "<!-- Benchmark ArrayStringAttribute (array) -->" << std::endl;
        ptr = AttributeFactory::createAttribute("a-string", AttrConfig(BasicType::STRING, CollectionType::ARRAY));
        benchmarkString(ptr);

    } else if (_config._attribute == "ws-string") {
        std::cout << "<!-- Benchmark WeightedSetStringAttribute (wset) -->" << std::endl;
        ptr = AttributeFactory::createAttribute("ws-string", AttrConfig(BasicType::STRING, CollectionType::WSET));
        benchmarkString(ptr);

    } else if (_config._attribute == "s-fs-string") {
        std::cout << "<!-- Benchmark SingleValueStringPostingAttribute (single fast search) -->" << std::endl;
        AttrConfig cfg(BasicType::STRING, CollectionType::SINGLE);
        cfg.setFastSearch(true);
        ptr = AttributeFactory::createAttribute("s-fs-string", cfg);
        benchmarkString(ptr);

    } else if (_config._attribute == "a-fs-string") {
        std::cout << "<!-- Benchmark ArrayStringPostingAttribute (array fast search) -->" << std::endl;
        AttrConfig cfg(BasicType::STRING, CollectionType::ARRAY);
        cfg.setFastSearch(true);
        ptr = AttributeFactory::createAttribute("a-fs-string", cfg);
        benchmarkString(ptr);

    } else if (_config._attribute == "ws-fs-string") {
        std::cout << "<!-- Benchmark WeightedSetStringPostingAttribute (wset fast search) -->" << std::endl;
        AttrConfig cfg(BasicType::STRING, CollectionType::WSET);
        cfg.setFastSearch(true);
        ptr = AttributeFactory::createAttribute("ws-fs-string", cfg);
        benchmarkString(ptr);

    }

    if (dc._writeAttribute) {
        std::cout << "<!-- Writing attribute to disk -->" << std::endl;
        ptr->saveAs(ptr->getBaseFileName());
    }

    std::cout << "</attribute-benchmark>" << std::endl;

    return 0;
}
}

int main(int argc, char ** argv)
{
    search::AttributeBenchmark myapp;
    return myapp.Entry(argc, argv);
}

