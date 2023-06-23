// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "common.h"
#include "allocchunk.h"
#include "globalpool.h"
#include "mmappool.h"
#include <atomic>

namespace vespamalloc {

constexpr int MMAP_LIMIT_MIN     =   0x100000; //  1M
constexpr int MMAP_LIMIT_DEFAULT =  0x4000000; // 64M
constexpr int MMAP_LIMIT_MAX     = 0x40000000; //  1G

template <typename MemBlockPtrT, typename ThreadStatT >
class ThreadPoolT
{
public:
    using ChunkSList = AFList<MemBlockPtrT>;
    using AllocPool = AllocPoolT<MemBlockPtrT>;
    using DataSegment = segment::DataSegment;
    ThreadPoolT();
    ~ThreadPoolT();
    void setPool(AllocPool & allocPool, MMapPool & mmapPool) {
        _allocPool = & allocPool;
        _mmapPool = & mmapPool;
    }
    int mallopt(int param, int value);
    void malloc(size_t sz, MemBlockPtrT & mem);
    void free(MemBlockPtrT mem, SizeClassT sc);

    void info(FILE * os, size_t level, const DataSegment & ds) const __attribute__((noinline));
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
                ASSERT_STACKTRACE(_allocFrom != nullptr);
                _freeTo = allocPool.getFree(sc, 1);
                ASSERT_STACKTRACE(_freeTo != nullptr);
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
    MMapPool    * _mmapPool;
    size_t        _mmapLimit;
    AllocFree     _memList[NUM_SIZE_CLASSES];
    ThreadStatT   _stat[NUM_SIZE_CLASSES];
    uint32_t      _threadId;
    std::atomic<ssize_t> _osThreadId;

    static constexpr SizeClassT ALWAYS_REUSE_SC_LIMIT = std::max(MemBlockPtrT::sizeClass(ALWAYS_REUSE_LIMIT),
                                                                 SizeClassT(MemBlockPtrT::SizeClassSpan));
    static size_t     _threadCacheLimit __attribute__((visibility("hidden")));
};

}
