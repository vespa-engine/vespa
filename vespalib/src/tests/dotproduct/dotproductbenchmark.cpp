// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <iostream>
#include <vector>
#include <thread>
#include <functional>

using namespace vespalib;
using vespalib::hwaccelrated::IAccelrated;

class Benchmark {
public:
    virtual ~Benchmark() { }
    virtual void compute(size_t docId) const = 0;
};

void
runThread(size_t count, size_t docs, const Benchmark * benchmark, size_t stride)
{
    for (size_t i(0); i < count; i++) {
        for (size_t docId(0); docId < docs; docId++) {
            benchmark->compute((docId*stride)%docs);
        }
    }
}

void
runBenchmark(size_t numThreads, size_t count, size_t docs, const Benchmark & benchmark, size_t stride) {
    if (numThreads > 1) {
        std::vector<std::thread> threads;
        threads.reserve(numThreads);
        for (size_t i(0); i < numThreads; i++) {
            threads.emplace_back(runThread, count, docs, &benchmark, stride);
        }
        for (auto & thread : threads) {
            thread.join();
        }
    } else {
        runThread(count, docs, &benchmark, stride);
    }
}

template <typename T>
class FullBenchmark : public Benchmark
{
public:
    FullBenchmark(size_t numDocs, size_t numValue);
    ~FullBenchmark();
    void compute(size_t docId) const override {
        _dp.dotProduct(&_query[0], &_values[docId * _query.size()], _query.size());
    }
private:
    std::vector<T> _values;
    std::vector<T> _query;
    const IAccelrated & _dp;
};

template <typename T>
FullBenchmark<T>::FullBenchmark(size_t numDocs, size_t numValues)
    : _values(numDocs*numValues),
      _query(numValues),
      _dp(IAccelrated::getAccelerator())
{
    for (size_t i(0); i < numDocs; i++) {
        for (size_t j(0); j < numValues; j++) {
            _values[i*numValues + j] = j;
        }
    }
    for (size_t j(0); j < numValues; j++) {
        _query[j] = j;
    }
}

template <typename T>
FullBenchmark<T>::~FullBenchmark() = default;

class SparseBenchmark : public Benchmark
{
public:
    SparseBenchmark(size_t numDocs, size_t numValues, size_t numQueryValues);
    ~SparseBenchmark();
protected:
    struct P {
        P(uint32_t key=0, int32_t value=0) noexcept :
            _key(key),
            _value(value)
        { }
        uint32_t _key;
        int32_t  _value;
    };
    size_t _numValues;
    std::vector<P> _values;
};

SparseBenchmark::SparseBenchmark(size_t numDocs, size_t numValues, size_t numQueryValues)
    : _numValues(numValues),
      _values(numDocs*numValues)
{
    for (size_t i(0); i < numDocs; i++) {
        for (size_t j(0); j < numValues; j++) {
            size_t k(numValues < numQueryValues ?  (j*numQueryValues)/numValues : j);
            _values[i*numValues + j] = P(k, k);
        }
    }
}
SparseBenchmark::~SparseBenchmark() = default;

std::function<void(int64_t)> use_sum = [](int64_t) noexcept { };

class UnorderedSparseBenchmark : public SparseBenchmark
{
private:
    typedef hash_map<uint32_t, int32_t> map;
public:
    UnorderedSparseBenchmark(size_t numDocs, size_t numValues, size_t numQueryValues);
    ~UnorderedSparseBenchmark();
private:
    void compute(size_t docId) const override {
        int64_t sum(0);
        size_t offset(docId*_numValues);
        const auto e(_query.end());
        for (size_t i(0); i < _numValues; i++) {
            auto it = _query.find(_values[offset + i]._key);
            if (it != e) {
                sum += static_cast<int64_t>(_values[offset + i]._value) * it->second;
            }
        }
        use_sum(sum);
    }
    map _query;
};

UnorderedSparseBenchmark::UnorderedSparseBenchmark(size_t numDocs, size_t numValues, size_t numQueryValues)
    : SparseBenchmark(numDocs, numValues, numQueryValues)
{
    for (size_t j(0); j < numQueryValues; j++) {
        _query[j] = j;
    }
}
UnorderedSparseBenchmark::~UnorderedSparseBenchmark() = default;

class OrderedSparseBenchmark : public SparseBenchmark
{
private:
public:
    OrderedSparseBenchmark(size_t numDocs, size_t numValues, size_t numQueryValues);
    ~OrderedSparseBenchmark();
private:
    void compute(size_t docId) const override {
        int64_t sum(0);
        size_t offset(docId*_numValues);

        for (size_t a(0), b(0); a < _query.size() && b < _numValues; b++) {
            for (; a < _query.size() && (_query[a]._key <= _values[offset + b]._key); a++);
            if (_query[a]._key == _values[offset + b]._key) {
                sum += static_cast<int64_t>(_values[offset + b]._value) * _query[a]._value;
            }
        }
        use_sum(sum);
    }
    std::vector<P> _query;
};

OrderedSparseBenchmark::OrderedSparseBenchmark(size_t numDocs, size_t numValues, size_t numQueryValues)
    : SparseBenchmark(numDocs, numValues, numQueryValues),
      _query(numQueryValues)
{
    for (size_t j(0); j < numQueryValues; j++) {
        size_t k(numValues > numQueryValues ?  j*numValues/numQueryValues : j);
        _query[j] = P(k, k);
    }
}
OrderedSparseBenchmark::~OrderedSparseBenchmark() = default;

int main(int argc, char *argv[])
{
    size_t numThreads(1);
    size_t numDocs(1);
    size_t numValues(1000);
    size_t numQueries(1000000);
    size_t stride(1);
    string type("full");
    if ( argc > 1) {
        type = argv[1];
    }
    if ( argc > 2) {
        numThreads = strtoul(argv[2], nullptr, 0);
    }
    if ( argc > 3) {
        numQueries = strtoul(argv[3], nullptr, 0);
    }
    if ( argc > 4) {
        numDocs = strtoul(argv[4], nullptr, 0);
    }
    if ( argc > 5) {
        numValues = strtoul(argv[5], nullptr, 0);
    }
    if (argc > 6) {
        stride = strtoul(argv[6], nullptr, 0);
    }
    size_t numQueryValues = ( argc > 7) ? strtoul(argv[7], nullptr, 0) : numValues;
    std::cout << "type = " << type << std::endl;
    std::cout << "numQueries = " << numQueries << std::endl;
    std::cout << "numDocs = " << numDocs << std::endl;
    std::cout << "numValues = " << numValues << std::endl;
    std::cout << "numQueryValues = " << numQueryValues << std::endl;
    std::cout << "stride =" << stride << std::endl;
    if (type == "full") {
        FullBenchmark<float> bm(numDocs, numValues);
        runBenchmark(numThreads, numQueries, numDocs, bm, stride);
    } else if (type == "sparse-ordered") {
        OrderedSparseBenchmark bm(numDocs, numValues, numQueryValues);
        runBenchmark(numThreads, numQueries, numDocs, bm, stride);
    } else if (type == "sparse-unordered") {
        UnorderedSparseBenchmark bm(numDocs, numValues, numQueryValues);
        runBenchmark(numThreads, numQueries, numDocs, bm, stride);
    } else {
        std::cerr << "type '" << type << "' is unknown." << std::endl;
    }
    
    return 0;
}

