// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "common.h"

namespace vespamalloc::segment {

using BlockIdT = uint32_t;
enum { UNMAPPED_BLOCK=-4, UNUSED_BLOCK=-3, FREE_BLOCK=-2, SYSTEM_BLOCK=-1, NUM_ADMIN_CLASSES=4 };

inline
const char *
getAdminClassName(int id) {
    switch (id) {
      case UNMAPPED_BLOCK: return "UNMAPPED";
      case   UNUSED_BLOCK: return "UNUSED";
      case     FREE_BLOCK: return "FREE";
      case   SYSTEM_BLOCK: return "SYSTEM";
      default:             return "UNKNOWN";
    }
}

// Allow for 1T heap
static constexpr size_t BlockSize = 0x200000ul;
static constexpr BlockIdT BlockCount = 0x80000;

inline
BlockIdT
blockId(const void * ptr) {
    return (size_t(ptr) - Memory::getMinPreferredStartAddress())/BlockSize;
}

inline
void *
fromBlockId(size_t id) {
    return reinterpret_cast<void *>(id*BlockSize + Memory::getMinPreferredStartAddress());
}

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
    template<typename MemBlockPtrT>
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

}
