// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "freelist.h"
#include <climits>

namespace vespamalloc::segment {


template <int MaxCount>
FreeListT<MaxCount>::FreeListT(BlockT * blockList) :
    _blockList(blockList),
    _count(0)
{
    for (size_t i = 0; i < NELEMS(_freeStartIndex); i++) {
        _freeStartIndex[i] = -1;
    }
}

template <int MaxCount>
FreeListT<MaxCount>::~FreeListT() = default;

template <int MaxCount>
void
FreeListT<MaxCount>::add(Index startIndex)
{
    Index i(0);
    Index numBlocks(_blockList[startIndex].freeChainLength());
    for (i=0; (i < _count) && (_freeStartIndex[i] < startIndex); i++) { }
    Index prevIndex(0), nextIndex(0);
    BlockT * prev(nullptr), * next(nullptr);
    if (i > 0) {
        prevIndex = _freeStartIndex[i-1];
        prev = & _blockList[prevIndex];
    }
    if (i < _count) {
        nextIndex = _freeStartIndex[i];
        next = & _blockList[nextIndex];
    }

    if (prev && (prevIndex + prev->freeChainLength() == startIndex)) {
        // Join with freeChain ahead.
        prev->freeChainLength(prev->freeChainLength() + numBlocks);
        startIndex = prevIndex;
    } else if (next && (startIndex + numBlocks == nextIndex)) {
        // Join with freeChain that follows.
        _freeStartIndex[i] = startIndex;
        nextIndex = startIndex;
        Index oldNextCount = next->freeChainLength();
        next = & _blockList[startIndex];
        next->freeChainLength(oldNextCount + numBlocks);
    } else {
        // Insert.
        for(Index j=0; j < (_count-i); j++) {
            _freeStartIndex[_count-j] = _freeStartIndex[_count-j-1];
        }
        _count++;
        _freeStartIndex[i] = startIndex;
    }

    if (prev && next && (prevIndex + prev->freeChainLength() == nextIndex)) {
        prev->freeChainLength(prev->freeChainLength() + next->freeChainLength());
        _count--;
        for(Index j=i; j < _count; j++) {
            _freeStartIndex[j] = _freeStartIndex[j+1];
        }
        _freeStartIndex[_count] = -1;
    }
}

template <int MaxCount>
void *
FreeListT<MaxCount>::sub(Index numBlocks)
{
    void * block(nullptr);
    size_t bestFitIndex(_count);
    int bestLeft(INT_MAX);
    for(size_t i=0; i < _count; i++) {
        size_t index(_freeStartIndex[i]);
        BlockT & b = _blockList[index];
        int left = b.freeChainLength() - numBlocks;
        if ((left >= 0) && (left < bestLeft)) {
            bestLeft = left;
            bestFitIndex = i;
        }
    }
    if (bestLeft != INT_MAX) {
        block = linkOut(bestFitIndex, bestLeft);
    }
    return block;
}

template <int MaxCount>
uint32_t
FreeListT<MaxCount>::lastBlock(Index nextBlock)
{
    Index lastIndex(0);
    if (_count > 0) {
        Index index(_freeStartIndex[_count-1]);
        BlockT & b = _blockList[index];
        if (index + b.freeChainLength() == nextBlock) {
            lastIndex = index;
        }
    }
    return lastIndex;
}

template <int MaxCount>
void
FreeListT<MaxCount>::info(FILE * os)
{
    for (Index i=0; i < _count; i++) {
        Index index(_freeStartIndex[i]);
        const BlockT & b = _blockList[index];
        fprintf(os, "Free #%3d block #%5d chainlength %5d size %10lu\n",
                i, index, b.freeChainLength(), b.freeChainLength()*BlockSize);
    }
}

template <int MaxCount>
uint32_t
FreeListT<MaxCount>::numFreeBlocks() const
{
    Index freeBlockCount(0);
    for (Index i=0; i < _count; i++) {
        Index index(_freeStartIndex[i]);
        const BlockT & b = _blockList[index];
        freeBlockCount += b.freeChainLength();
    }
    return freeBlockCount;
}

template <int MaxCount>
void *
FreeListT<MaxCount>::linkOut(Index findex, Index left)
{
    size_t index(_freeStartIndex[findex]);
    BlockT & b = _blockList[index];
    Index startIndex = index + left;
    void *block = fromBlockId(startIndex);
    if (left > 0) {
        b.freeChainLength(left);
    } else {
        _count--;
        for(Index j=findex; j < (_count); j++) {
            _freeStartIndex[j] = _freeStartIndex[j+1];
        }
        _freeStartIndex[_count] = -1;
    }
    return block;
}

}
