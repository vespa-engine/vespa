// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <dlfcn.h>
#include <csignal>
#include <stdlib.h>
#include <cassert>
#include <vector>
#include <vespamalloc/util/index.h>
#include <vespamalloc/util/callstack.h>
#include <vespamalloc/util/callgraph.h>


namespace vespamalloc {

using StackElem = StackEntry;
typedef CallGraph<StackElem, 0x10000, Index> CallGraphT;

class Aggregator
{
public:
    Aggregator();
    ~Aggregator();
    void push_back(size_t num, const string & s) { _map.emplace_back(num, s); }
    friend asciistream & operator << (asciistream & os, const Aggregator & v);
private:
    typedef std::vector< std::pair<size_t, string> > Map;
    Map _map;
};


template<typename N>
class DumpGraph
{
public:
    DumpGraph(Aggregator * aggregator, const char * s="{ ", const char * end=" }") __attribute__ ((noinline));
    ~DumpGraph() __attribute__ ((noinline));
    void handle(const N & node) __attribute__ ((noinline));
private:
    string       _string;
    string       _endString;
    size_t       _sum;
    size_t       _min;
    Aggregator * _aggregator;
};

asciistream & operator << (asciistream & os, const Aggregator & v);

template<typename N>
DumpGraph<N>::DumpGraph(Aggregator * aggregator, const char * start, const char * end) :
    _string(start),
    _endString(end),
    _sum(0),
    _min(-1),
    _aggregator(aggregator)
{
}

template<typename N>
DumpGraph<N>::~DumpGraph() = default;

template<typename N>
void DumpGraph<N>::handle(const N & node)
{
    _sum += node.count();
    if (node.count() < _min) {
        _min = node.count();
    }
    asciistream os;
    os << ' ' << node;
    _string += os.c_str();
    if (node.callers() == nullptr) {
        _string += _endString;
        _aggregator->push_back(_min, _string);
    }
}

}

