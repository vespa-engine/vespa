// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/common/sort.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/util/buffer.h>
#include <vespa/vespalib/stllike/string.h>

using vespalib::Array;
using vespalib::ConstBufferRef;

class Test : public vespalib::TestApp
{
public:
    using V = std::vector<uint32_t>;
    std::vector< std::vector<uint32_t> > _data;
    int Main() override;
    void generateVectors(size_t numVectors, size_t values);
    V merge();
    void twoWayMerge();
    V cat() const;
    Test();
    ~Test() override;
};

Test::Test() = default;
Test::~Test() = default;

void
Test::generateVectors(size_t numVectors, size_t values)
{
    _data.resize(numVectors);
    for (size_t j(0); j < numVectors; j++) {
        V & v(_data[j]);
        v.resize(values);
        for (size_t i(0); i < values; i++) {
            v[i] = i;
        }
    }
}

Test::V
Test::merge()
{
    twoWayMerge();
    return _data[0];
}

void
Test::twoWayMerge()
{
    std::vector<V> n((_data.size()+1)/2);

    for ( size_t i(0), m(_data.size()/2); i < m; i++) {
        const V & a = _data[i*2 + 0];
        const V & b = _data[i*2 + 1];
        n[i].resize(a.size() + b.size());
        std::merge(a.begin(), a.end(), b.begin(), b.end(), n[i].begin());
    }
    if (_data.size()%2) {
        n[n.size()-1].swap(_data[_data.size() - 1]);
    }
    _data.swap(n);
    if (_data.size() > 1) {
        twoWayMerge();
    }
}

Test::V
Test::cat() const
{
    size_t sum(0);
    for (size_t i(0), m(_data.size()); i < m; i++) {
        sum += _data[i].size();
    }
    V c;
    c.reserve(sum);
    for (size_t i(0), m(_data.size()); i < m; i++) {
        const V & v(_data[i]);
        c.insert(c.end(), v.begin(), v.end());
    }

    return c;
}

TEST_APPHOOK(Test);

int Test::Main()
{
    TEST_INIT("sortbenchmark");
    size_t numVectors(11);
    size_t values(10000000);
    vespalib::string type("radix");
    if (_argc > 1) {
        values = strtol(_argv[1], NULL, 0);
        if (_argc > 2) {
            numVectors = strtol(_argv[2], NULL, 0);
            if (_argc > 2) {
                type = _argv[3];
            }
        }
    }

    printf("Start with %ld vectors with %ld values and type '%s'(radix, qsort, merge)\n", numVectors, values, type.c_str());
    generateVectors(numVectors, values);
    printf("Start cat\n");
    V v = cat();
    printf("Cat %ld values\n", v.size());
    if (type == "merge") {
        V m = merge();
        printf("Merged %ld values\n", m.size());
    } else if (type == "qsort") {
        std::sort(v.begin(), v.end());
        printf("sorted %ld value with std::sort\n", v.size());
    } else {
        search::NumericRadixSorter<uint32_t, true> S;
        S(&v[0], v.size());
        printf("sorted %ld value with radix::sort\n", v.size());
    }

    TEST_DONE();
}
