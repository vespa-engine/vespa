// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <atomic>
#include <stdio.h>

namespace vespamalloc {

class Index
{
public:
    typedef size_t index_t;
    Index(index_t index = 0) : _index(index) { }
    operator index_t ()       const { return _index; }
    index_t operator ++ (int)       { return _index++; }
    index_t operator -- (int)       { return _index--; }
    index_t operator += (index_t v) { return _index += v; }
    index_t operator -= (index_t v) { return _index -= v; }
private:
    index_t _index;
};

class AtomicIndex
{
public:
    typedef size_t index_t;
    AtomicIndex(index_t index = 0) : _index(index) { }
    operator index_t ()       const { return _index; }
    index_t operator ++ (int)       { return _index.fetch_add(1); }
    index_t operator -- (int)       { return _index.fetch_sub(1); }
    index_t operator += (index_t v) { return _index.fetch_add(v); }
    index_t operator -= (index_t v) { return _index.fetch_sub(v); }
private:
    std::atomic<index_t> _index;
};

}

