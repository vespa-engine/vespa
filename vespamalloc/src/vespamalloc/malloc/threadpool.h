// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <atomic>
#include <vespamalloc/malloc/common.h>
#include <vespamalloc/malloc/allocchunk.h>
#include <vespamalloc/malloc/globalpool.h>

namespace vespamalloc {

template <typename MemBlockPtrT, typename ThreadStatT >
class ThreadPoolT
{
public:
    typedef AFList<MemBlockPtrT> ChunkSList;
    typedef AllocPoolT<MemBlockPtrT> AllocPool;
    ThreadPoolT();
    ~ThreadPoolT();
    void setPool(AllocPool & pool) {
        _allocPool = & pool;
    }
    void malloc(size_t sz, MemBlockPtrT & mem);
    void free(MemBlockPtrT mem, SizeClassT sc);

    void info(FILE * os, size_t level, const DataSegment<MemBlockPtrT> & ds) const __attribute__((noinline));
    /**
     * Indicates if it represents an active thread.
     * @return true if this represents an active thread.
     */
    bool isActive() const;
    /**
     * Indicates if it represents an active thread that actually has done any allocations/deallocations.
     * @return true if this represents an active used thread.
     */
    bool isUsed() const;
    int osThreadId()       const { return _osThreadId; }
    uint32_t threadId()    const { return _threadId; }
    void quit() { _osThreadId = 0; } // Implicit memory barrier
    void init(int thrId);
    static void setParams(size_t threadCacheLimit);
    bool grabAvailable();
private:
    bool hasActuallyBeenUsed() const;
    ThreadPoolT(const ThreadPoolT & rhs);
    ThreadPoolT & operator =(const ThreadPoolT & rhs);
    void setThreadId(uint32_t th)   { _threadId = th; }
    class AllocFree {
    public:
        AllocFree() : _allocFrom(nullptr), _freeTo(nullptr) { }
        void init(AllocPool & allocPool, SizeClassT sc) {
            if (_allocFrom == nullptr) {
                _allocFrom = allocPool.getFree(sc, 1);
                assert(_allocFrom != nullptr);
                _freeTo = allocPool.getFree(sc, 1);
                assert(_freeTo != nullptr);
            }
        }
        void swap() {
            std::swap(_allocFrom, _freeTo);
        }
        ChunkSList *_allocFrom;
        ChunkSList *_freeTo;
    };
    void mallocHelper(size_t exactSize, SizeClassT sc, AllocFree & af, MemBlockPtrT & mem) __attribute__ ((noinline));
    static constexpr bool alwaysReuse(SizeClassT sc) { return sc > ALWAYS_REUSE_SC_LIMIT; }

    AllocPool   * _allocPool;
    AllocFree     _memList[NUM_SIZE_CLASSES];
    ThreadStatT   _stat[NUM_SIZE_CLASSES];
    uint32_t      _threadId;
    std::atomic<ssize_t> _osThreadId;

    static constexpr SizeClassT ALWAYS_REUSE_SC_LIMIT = std::max(MemBlockPtrT::sizeClass(ALWAYS_REUSE_LIMIT),
                                                                 SizeClassT(MemBlockPtrT::SizeClassSpan));
    static size_t     _threadCacheLimit __attribute__((visibility("hidden")));
};

}
