// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespamalloc/util/traceutil.h>
#include <algorithm>

namespace vespamalloc {

Aggregator::Aggregator() = default;

Aggregator::~Aggregator() = default;

struct CmpGraph
{
    bool operator () (const std::pair<size_t, string> & a, const std::pair<size_t, string> & b) const {
        return a.first < b.first;
    }
};

asciistream & operator << (asciistream & os, const Aggregator & v)
{
    Aggregator::Map map(v._map);
    std::sort(map.begin(), map.end(), CmpGraph());
    for (const auto & e : map) {
        os << e.first << " : " << e.second.c_str() << '\n';
    }
    return os;
}

}
