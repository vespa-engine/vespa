// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "common.h"
#include "freelist.h"
#include <vespamalloc/util/traceutil.h>
#include <vespamalloc/util/stream.h>

namespace vespamalloc::segment {

class IMemBlockInfo {
public:
    virtual ~IMemBlockInfo() = default;
    virtual bool allocated() const = 0;
    virtual uint32_t threadId() const = 0;
    virtual void info(FILE * os, int level) const = 0;
    virtual uint32_t callStackLen() const = 0;
    virtual const StackEntry * callStack() const = 0;
};
class IHelper {
public:
    virtual ~IHelper() = default;
    virtual size_t classSize(SizeClassT sc) const = 0;
    virtual void dumpInfo(int level) const = 0;
    virtual std::unique_ptr<IMemBlockInfo> createMemblockInfo(void * ptr) const = 0;
};

class DataSegment
{
public:
    DataSegment(const DataSegment & rhs) = delete;
    DataSegment & operator = (const DataSegment & rhs) = delete;
    explicit DataSegment(const IHelper & helper) __attribute__((noinline));
    ~DataSegment() __attribute__((noinline));

    void * getBlock(size_t & oldBlockSize, SizeClassT sc) __attribute__((noinline));
    void returnBlock(void *ptr) __attribute__((noinline));
    SizeClassT sizeClass(const void * ptr)    const { return _blockList[blockId(ptr)].sizeClass(); }
    bool containsPtr(const void * ptr)        const { return blockId(ptr) < BlockCount; }
    template<typename MemBlockPtrT>
    size_t getMaxSize(const void * ptr)       const { return _blockList[blockId(ptr)].getMaxSize<MemBlockPtrT>(); }
    const void * start()                      const { return _osMemory.getStart(); }
    const void * end()                        const { return _osMemory.getEnd(); }
    static SizeClassT adjustedSizeClass(size_t sz)  { return (sz >> 16) + 0x400; }
    static size_t adjustedClassSize(SizeClassT sc)  { return (sc > 0x400) ? (sc - 0x400) << 16 : sc; }
    size_t dataSize()                         const { return (const char*)end() - (const char*)start(); }
    size_t freeSize() const;
    size_t infoThread(FILE * os, int level, uint32_t thread, SizeClassT sct, uint32_t maxThreadId=0) const __attribute__((noinline));
    void info(FILE * os, size_t level) __attribute__((noinline));
    void setupLog(size_t bigMemLogLevel, size_t bigLimit, size_t bigIncrement, size_t allocs2Show) {
        _bigSegmentLogLevel = bigMemLogLevel;
        if ((size_t(end()) < _nextLogLimit) || (size_t(end()) < (size_t(start()) + bigLimit))) {
            _nextLogLimit = size_t(start()) + bigLimit;
        }
        _bigIncrement = bigIncrement;
        _allocs2Show = allocs2Show;
        checkAndLogBigSegment();
    }
    void enableThreadSupport() { _mutex.init(); }

private:

    void checkAndLogBigSegment() __attribute__((noinline));

    typedef BlockT BlockList[BlockCount];
    typedef FreeListT<BlockCount/2> FreeList;
    OSMemory        _osMemory;
    size_t          _bigSegmentLogLevel;
    size_t          _bigIncrement;
    size_t          _allocs2Show;
    size_t          _unmapSize;
    size_t          _nextLogLimit;
    size_t          _partialExtension;
    const IHelper  &_helper;

    Mutex           _mutex;
    BlockList       _blockList;
    FreeList        _freeList;
    FreeList        _unMappedList;
};

}
