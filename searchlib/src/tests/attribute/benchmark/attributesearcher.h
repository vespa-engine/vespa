// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/search_context.h>
#include <vespa/searchlib/queryeval/hitcollector.h>
#include <vespa/searchlib/queryeval/executeinfo.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/compress.h>
#include <vespa/searchlib/parsequery/parse.h>

namespace search {

std::unique_ptr<ResultSet>
performSearch(queryeval::SearchIterator & sb, uint32_t numDocs)
{
    queryeval::HitCollector hc(numDocs, numDocs);
    // assume strict toplevel search object located at start
    for (sb.seek(1); ! sb.isAtEnd(); sb.seek(sb.getDocId() + 1)) {
        hc.addHit(sb.getDocId(), 0.0);
    }
    return hc.getResultSet();
}

class AttributeSearcherStatus
{
public:
    vespalib::duration _totalSearchTime;
    uint64_t _totalHitCount;
    uint64_t _numQueries;
    uint64_t _numClients;

    AttributeSearcherStatus() : _totalSearchTime(0), _totalHitCount(0), _numQueries(0), _numClients(0) {}
    void merge(const AttributeSearcherStatus & status) {
        _totalSearchTime += status._totalSearchTime;
        _totalHitCount += status._totalHitCount;
        _numQueries += status._numQueries;
        _numClients += status._numClients;
    }
    void printXML() const {
        std::cout << "<total-search-time>" << vespalib::count_ms(_totalSearchTime) << "</total-search-time>" << std::endl; // ms
        std::cout << "<avg-search-time>" << avgSearchTime() << "</avg-search-time>" << std::endl; // ms
        std::cout << "<search-throughput>" << searchThroughout() << "</search-throughput>" << std::endl; // per/sec
        std::cout << "<total-hit-count>" << _totalHitCount << "</total-hit-count>" << std::endl;
        std::cout << "<avg-hit-count>" << avgHitCount() << "</avg-hit-count>" << std::endl;
    }
    double avgSearchTime() const {
        return vespalib::count_ms(_totalSearchTime/_numQueries);
    }
    double searchThroughout() const {
        return _numClients * 1000 * _numQueries / (vespalib::count_ns(_totalSearchTime)/1000000.0);
    }
    double avgHitCount() const {
        return _totalHitCount / double(_numQueries);
    }
};


class AttributeSearcher
{
protected:
    using AttributePtr = AttributeVector::SP;

    const AttributePtr    & _attrPtr;
    vespalib::Timer         _timer;
    AttributeSearcherStatus _status;
    std::thread             _thread;
    
public:
    AttributeSearcher(const AttributePtr & attrPtr)
      : _attrPtr(attrPtr), _timer(), _status(), _thread()
    {
        _status._numClients = 1;
    }
    virtual ~AttributeSearcher();
    virtual void doRun() = 0;
    void start() { _thread = std::thread([this](){doRun();}); }
    void join() { _thread.join(); }
    AttributeSearcherStatus & getStatus() { return _status; }
    void buildTermQuery(std::vector<char> & buffer, const vespalib::string & index, const char * term, bool prefix = false);
};
AttributeSearcher::~AttributeSearcher() = default;


void
AttributeSearcher::buildTermQuery(std::vector<char> & buffer, const vespalib::string & index, const char * term, bool prefix)
{
    uint32_t indexLen = index.size();
    uint32_t termLen = strlen(term);
    uint32_t termIdx = prefix ? ParseItem::ITEM_PREFIXTERM : ParseItem::ITEM_TERM;
    uint32_t queryPacketSize = vespalib::compress::Integer::compressedPositiveLength(termIdx)
                             + vespalib::compress::Integer::compressedPositiveLength(indexLen)
                             + vespalib::compress::Integer::compressedPositiveLength(termLen)
                             + indexLen + termLen;
    buffer.resize(queryPacketSize);
    char * p = &buffer[0];
    p += vespalib::compress::Integer::compressPositive(termIdx, p);
    p += vespalib::compress::Integer::compressPositive(indexLen, p);
    memcpy(p, index.c_str(), indexLen);
    p += indexLen;
    p += vespalib::compress::Integer::compressPositive(termLen, p);
    memcpy(p, term, termLen);
    p += termLen;
    assert(p == (&buffer[0] + buffer.size()));
}


template <typename T>
class AttributeFindSearcher : public AttributeSearcher
{
private:
    const std::vector<T> & _values;
    std::vector<char> _query;

public:
    AttributeFindSearcher(const AttributePtr & attrPtr, const std::vector<T> & values,
                          uint32_t numQueries) :
        AttributeSearcher(attrPtr), _values(values), _query()
    {
        _status._numQueries = numQueries;
    }
    void doRun() override;
};

template <typename T>
void
AttributeFindSearcher<T>::doRun()
{
    _timer = vespalib::Timer();
    for (uint32_t i = 0; i < _status._numQueries; ++i) {
        // build simple term query
        vespalib::asciistream ss;
        ss << _values[i % _values.size()].getValue();
        this->buildTermQuery(_query, _attrPtr->getName(), ss.str().data());

        AttributeGuard guard(_attrPtr);
        std::unique_ptr<attribute::SearchContext> searchContext =
            _attrPtr->getSearch(vespalib::stringref(&_query[0], _query.size()),
                                attribute::SearchContextParams());

        searchContext->fetchPostings(queryeval::ExecuteInfo::TRUE);
        std::unique_ptr<queryeval::SearchIterator> iterator = searchContext->createIterator(nullptr, true);
        std::unique_ptr<ResultSet> results = performSearch(*iterator, _attrPtr->getNumDocs());

        _status._totalHitCount += results->getNumHits();
    }
    _status._totalSearchTime += _timer.elapsed();
}


class RangeSpec
{
public:
    int64_t _min;
    int64_t _max;
    int64_t _range;
    RangeSpec(int64_t min, int64_t max, int64_t range) :
        _min(min), _max(max), _range(range)
    {
        assert(_min < _max);
        assert(_range <= (_max - _min));
    }
};

class RangeIterator
{
private:
    RangeSpec _spec;
    int64_t _a;
    int64_t _b;

public:
    RangeIterator(const RangeSpec & spec) : _spec(spec), _a(spec._min), _b(spec._min + _spec._range) {}
    RangeIterator & operator++() {
        _a += _spec._range;
        _b += _spec._range;
        if (_b > _spec._max) {
            _a = _spec._min;
            _b = _spec._min + _spec._range;
        }
        return *this;
    }
    int64_t a() const { return _a; }
    int64_t b() const { return _b; }
};

class AttributeRangeSearcher : public AttributeSearcher
{
private:
    RangeSpec _spec;
    std::vector<char> _query;

public:
    AttributeRangeSearcher(const AttributePtr & attrPtr, const RangeSpec & spec,
                           uint32_t numQueries) :
        AttributeSearcher(attrPtr), _spec(spec), _query()
    {
        _status._numQueries = numQueries;
    }
    void doRun() override;
};

void
AttributeRangeSearcher::doRun()
{
    _timer = vespalib::Timer();
    RangeIterator iter(_spec);
    for (uint32_t i = 0; i < _status._numQueries; ++i, ++iter) {
        // build simple range term query
        vespalib::asciistream ss;
        ss << "[" << iter.a() << ";" << iter.b() << "]";
        buildTermQuery(_query, _attrPtr->getName(), ss.str().data());

        AttributeGuard guard(_attrPtr);
        std::unique_ptr<attribute::SearchContext> searchContext =
            _attrPtr->getSearch(vespalib::stringref(&_query[0], _query.size()),
                                attribute::SearchContextParams());

        searchContext->fetchPostings(queryeval::ExecuteInfo::TRUE);
        std::unique_ptr<queryeval::SearchIterator> iterator = searchContext->createIterator(nullptr, true);
        std::unique_ptr<ResultSet> results = performSearch(*iterator, _attrPtr->getNumDocs());

        _status._totalHitCount += results->getNumHits();
    }
    _status._totalSearchTime += _timer.elapsed();
}


class AttributePrefixSearcher : public AttributeSearcher
{
private:
    const std::vector<vespalib::string> & _values;
    std::vector<char> _query;

public:
    AttributePrefixSearcher(const AttributePtr & attrPtr,
                            const std::vector<vespalib::string> & values, uint32_t numQueries) :
        AttributeSearcher(attrPtr), _values(values), _query()
    {
        _status._numQueries = numQueries;
    }
    void doRun() override;
};

void
AttributePrefixSearcher::doRun()
{
    _timer = vespalib::Timer();
    for (uint32_t i = 0; i < _status._numQueries; ++i) {
        // build simple prefix term query
        buildTermQuery(_query, _attrPtr->getName(), _values[i % _values.size()].c_str(), true);

        AttributeGuard guard(_attrPtr);
        std::unique_ptr<attribute::SearchContext> searchContext =
            _attrPtr->getSearch(vespalib::stringref(&_query[0], _query.size()),
                                attribute::SearchContextParams());

        searchContext->fetchPostings(queryeval::ExecuteInfo::TRUE);
        std::unique_ptr<queryeval::SearchIterator> iterator = searchContext->createIterator(nullptr, true);
        std::unique_ptr<ResultSet> results = performSearch(*iterator, _attrPtr->getNumDocs());

        _status._totalHitCount += results->getNumHits();
    }
    _status._totalSearchTime += _timer.elapsed();
}

} // search
