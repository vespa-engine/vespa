// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "common.h"
#include "allocchunk.h"
#include "datasegment.h"
#include <algorithm>

#define USE_STAT2(a) a

namespace vespamalloc {

template <typename MemBlockPtrT>
class AllocPoolT
{
    using DataSegment = segment::DataSegment;
public:
    typedef AFList<MemBlockPtrT> ChunkSList;
    AllocPoolT(DataSegment & ds);
    ~AllocPoolT();

    ChunkSList *getFree(SizeClassT sc, size_t minBlocks);
    ChunkSList *exchangeFree(SizeClassT sc, ChunkSList * csl);
    ChunkSList *exchangeAlloc(SizeClassT sc, ChunkSList * csl);
    ChunkSList *exactAlloc(size_t exactSize, SizeClassT sc, ChunkSList * csl) __attribute__((noinline));
    ChunkSList *returnMemory(SizeClassT sc, ChunkSList * csl) __attribute__((noinline));

    DataSegment & dataSegment()      { return _dataSegment; }
    void enableThreadSupport() __attribute__((noinline));

    static void setParams(size_t threadCacheLimit);
    static size_t computeExactSize(size_t sz) {
        return (((sz + (ALWAYS_REUSE_LIMIT - 1)) / ALWAYS_REUSE_LIMIT) * ALWAYS_REUSE_LIMIT);
    }

    void info(FILE * os, size_t level=0) __attribute__((noinline));
private:
    ChunkSList * getFree(SizeClassT sc) __attribute__((noinline));
    ChunkSList * getAlloc(SizeClassT sc) __attribute__((noinline));
    ChunkSList * malloc(const Guard & guard, SizeClassT sc) __attribute__((noinline));
    ChunkSList * getChunks(const Guard & guard, size_t numChunks) __attribute__((noinline));
    ChunkSList * allocChunkList(const Guard & guard) __attribute__((noinline));
    AllocPoolT(const AllocPoolT & ap);
    AllocPoolT & operator = (const AllocPoolT & ap);

    class AllocFree
    {
    public:
        AllocFree() : _full(), _empty() { }
        typename ChunkSList::AtomicHeadPtr _full;
        typename ChunkSList::AtomicHeadPtr _empty;
    };
    class Stat
    {
    public:
        Stat() : _getAlloc(0),
                 _getFree(0),
                 _exchangeAlloc(0),
                 _exchangeFree(0),
                 _exactAlloc(0),
                 _return(0),_malloc(0) { }
        std::atomic<size_t> _getAlloc;
        std::atomic<size_t> _getFree;
        std::atomic<size_t> _exchangeAlloc;
        std::atomic<size_t> _exchangeFree;
        std::atomic<size_t> _exactAlloc;
        std::atomic<size_t> _return;
        std::atomic<size_t> _malloc;
        bool isUsed()       const {
            // Do not count _getFree.
            return (_getAlloc || _exchangeAlloc || _exchangeFree || _exactAlloc || _return || _malloc);
        }
    };

    Mutex                   _mutex;
    ChunkSList            * _chunkPool;
    AllocFree               _scList[NUM_SIZE_CLASSES];
    DataSegment           & _dataSegment;
    std::atomic<size_t>     _getChunks;
    std::atomic<size_t>     _getChunksSum;
    std::atomic<size_t>     _allocChunkList;
    Stat                    _stat[NUM_SIZE_CLASSES];
    static size_t           _threadCacheLimit __attribute__((visibility("hidden")));
};

}
