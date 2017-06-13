// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/sync.h>
#include <vector>

namespace storage {

namespace memfile {

class ThreadStatic {
public:
    static vespalib::Lock _threadLock;
    static uint16_t _nextThreadIdx;
    static __thread int _threadIdx;

    void initThreadIndex();
};

/**
 * This class takes ownership of a set of thread local
 * variables. The maximum number of unique threads the
 * class can use must be predetermined on construction.
 */
template<typename T>
class ThreadLocals : public ThreadStatic {
    static const size_t CACHE_LINE_SIZE = 64; // Architectural assumption.
    struct CacheLinePaddedValue
    {
        T _data;
    private:
        // Ensure addressing the data of one entry does not touch the cache
        // line of any following entries. Could make this an exact fit, but
        // not very important since there are very few TLS entries in total.
        char _padding[CACHE_LINE_SIZE];
    };
public:
    mutable std::vector<CacheLinePaddedValue> _contexts;

    ThreadLocals(uint32_t maxThreadCount)
        : _contexts(maxThreadCount)
    {
    }

    T& get() {
        initThreadIndex();
        assert(_threadIdx < (int)_contexts.size());
        return _contexts[_threadIdx]._data;
    }
};

}

}

