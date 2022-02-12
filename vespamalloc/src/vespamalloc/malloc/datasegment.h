// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <climits>
#include <memory>
#include <vespamalloc/malloc/common.h>
#include <vespamalloc/util/traceutil.h>
#include <vespamalloc/util/stream.h>

namespace vespamalloc {

template<typename MemBlockPtrT>
class DataSegment
{
public:
    using BlockIdT = uint32_t;
    enum { UNMAPPED_BLOCK=-4, UNUSED_BLOCK=-3, FREE_BLOCK=-2, SYSTEM_BLOCK=-1, NUM_ADMIN_CLASSES=4 };
    DataSegment() __attribute__((noinline));
    ~DataSegment() __attribute__((noinline));

    void * getBlock(size_t & oldBlockSize, SizeClassT sc) __attribute__((noinline));
    void returnBlock(void *ptr) __attribute__((noinline));
    SizeClassT sizeClass(const void * ptr)    const { return _blockList[blockId(ptr)].sizeClass(); }
    bool containsPtr(const void * ptr)        const { return blockId(ptr) < BlockCount; }
    size_t getMaxSize(const void * ptr)       const { return _blockList[blockId(ptr)].getMaxSize(); }
    const void * start()                      const { return _osMemory.getStart(); }
    const void * end()                        const { return _osMemory.getEnd(); }
    static SizeClassT adjustedSizeClass(size_t sz)  { return (sz >> 16) + 0x400; }
    static size_t adjustedClassSize(SizeClassT sc)  { return (sc > 0x400) ? (sc - 0x400) << 16 : sc; }
    size_t dataSize()                         const { return (const char*)end() - (const char*)start(); }
    size_t freeSize() const;
    size_t infoThread(FILE * os, int level, uint32_t thread, SizeClassT sct, uint32_t maxThreadId=0) const __attribute__((noinline));
    void info(FILE * os, size_t level) __attribute__((noinline));
    void setupLog(size_t bigMemLogLevel, size_t bigLimit, size_t bigIncrement, size_t allocs2Show)
    {
        _bigSegmentLogLevel = bigMemLogLevel;
        if ((size_t(end()) < _nextLogLimit) || (size_t(end()) < (size_t(start()) + bigLimit))) {
            _nextLogLimit = size_t(start()) + bigLimit;
        }
        _bigIncrement = bigIncrement;
        _allocs2Show = allocs2Show;
        checkAndLogBigSegment();
    }
    void enableThreadSupport() { _mutex.init(); }
    static BlockIdT blockId(const void * ptr)       {
        return (size_t(ptr) - Memory::getMinPreferredStartAddress())/BlockSize;
    }
    static void * fromBlockId(size_t id) {
        return reinterpret_cast<void *>(id*BlockSize + Memory::getMinPreferredStartAddress());
    }
private:
    const char * getAdminClassName(int id) {
        switch (id) {
          case UNMAPPED_BLOCK: return "UNMAPPED";
          case   UNUSED_BLOCK: return "UNUSED";
          case     FREE_BLOCK: return "FREE";
          case   SYSTEM_BLOCK: return "SYSTEM";
          default:             return "UNKNOWN";
        }
    }
    DataSegment(const DataSegment & rhs);
    DataSegment & operator = (const DataSegment & rhs);

    // Allow for 1T heap
    static constexpr size_t BlockSize = 0x200000ul;
    static constexpr BlockIdT BlockCount = 0x80000;

    class BlockT
    {
    public:
        BlockT(SizeClassT szClass = UNUSED_BLOCK, BlockIdT numBlocks = 0)
            : _sizeClass(szClass), _freeChainLength(0), _realNumBlocks(numBlocks)
        { }
        SizeClassT sizeClass()            const { return _sizeClass; }
        BlockIdT realNumBlocks()          const { return _realNumBlocks; }
        BlockIdT freeChainLength()        const { return _freeChainLength; }
        void sizeClass(SizeClassT sc)           { _sizeClass = sc; }
        void realNumBlocks(BlockIdT fc)         { _realNumBlocks = fc; }
        void freeChainLength(BlockIdT fc)       { _freeChainLength = fc; }
        size_t getMaxSize()               const {
            return MemBlockPtrT::unAdjustSize(std::min(MemBlockPtrT::classSize(_sizeClass),
                                                       size_t(_realNumBlocks) * BlockSize));
        }
    private:
        SizeClassT _sizeClass;
        /// Number of blocks free from here and on. For memory reuse, big blocks only.
        BlockIdT _freeChainLength;
        /// Real number of blocks used. Used to avoid rounding for big blocks.
        BlockIdT _realNumBlocks;
    };

    template <int MaxCount>
    class FreeListT {
    public:
        using Index = BlockIdT;
        FreeListT(BlockT * blockList) __attribute__((noinline));
        FreeListT(const FreeListT &) = delete;
        FreeListT & operator =(const FreeListT &) = delete;
        FreeListT(FreeListT &&) = delete;
        FreeListT & operator =(FreeListT &&) = delete;
        ~FreeListT();
        void add(Index startIndex) __attribute__((noinline));
        void * sub(Index numBlocks) __attribute__((noinline));
        Index lastBlock(Index nextBlock) __attribute__((noinline));
        void removeLastBlock() {
            if (_count > 0) {
                _count--;
            }
        }
        Index numFreeBlocks() const;
        void info(FILE * os) __attribute__((noinline));
    private:
        void * linkOut(Index findex, Index left) __attribute__((noinline));
        BlockT *_blockList;
        Index  _count;
        Index  _freeStartIndex[MaxCount];
    };

    void checkAndLogBigSegment() __attribute__((noinline));

    typedef BlockT BlockList[BlockCount];
    typedef FreeListT<BlockCount/2> FreeList;
    OSMemory     _osMemory;
    size_t       _bigSegmentLogLevel;
    size_t       _bigIncrement;
    size_t       _allocs2Show;
    size_t       _unmapSize;

    size_t       _nextLogLimit;
    size_t       _partialExtension;
    Mutex        _mutex;
    BlockList    _blockList;
    FreeList     _freeList;
    FreeList     _unMappedList;
};

}
