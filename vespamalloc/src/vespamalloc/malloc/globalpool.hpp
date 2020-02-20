// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "globalpool.h"

#define USE_STAT2(a) a

namespace vespamalloc {

template <typename MemBlockPtrT>
size_t AllocPoolT<MemBlockPtrT>::_threadCacheLimit __attribute__((visibility("hidden"))) = 0x10000;
template <typename MemBlockPtrT>
size_t AllocPoolT<MemBlockPtrT>::_alwaysReuseLimit __attribute__((visibility("hidden"))) = 0x200000;

template <typename MemBlockPtrT>
AllocPoolT<MemBlockPtrT>::AllocPoolT(DataSegment<MemBlockPtrT> & ds)
    : _chunkPool(NULL),
      _scList(),
      _dataSegment(ds),
      _getChunks(0),
      _getChunksSum(0),
      _allocChunkList(0),
      _stat()
{
}

template <typename MemBlockPtrT>
AllocPoolT<MemBlockPtrT>::~AllocPoolT()
{
}

template <typename MemBlockPtrT>
void AllocPoolT<MemBlockPtrT>::enableThreadSupport()
{
    _mutex.init();
}

template <typename MemBlockPtrT>
void
AllocPoolT<MemBlockPtrT>::setParams(size_t alwaysReuseLimit, size_t threadCacheLimit)
{
    _alwaysReuseLimit = alwaysReuseLimit;
    _threadCacheLimit = threadCacheLimit;
}

template <typename MemBlockPtrT>
typename AllocPoolT<MemBlockPtrT>::ChunkSList *
AllocPoolT<MemBlockPtrT>::getFree(SizeClassT sc)
{
    typename ChunkSList::AtomicHeadPtr & empty = _scList[sc]._empty;
    ChunkSList * csl(NULL);
    while ((csl = ChunkSList::linkOut(empty)) == NULL) {
        Guard sync(_mutex);
        if (empty.load(std::memory_order_relaxed)._ptr == NULL) {
            ChunkSList * ncsl(getChunks(sync, 1));
            if (ncsl) {
                ChunkSList::linkInList(empty, ncsl);
            } else {
                assert(ncsl != NULL);
                return NULL;
            }
        }
    }
    PARANOID_CHECK1( if ( !csl->empty()) { *(int*)0 = 0; } );
    return csl;
}

template <typename MemBlockPtrT>
typename AllocPoolT<MemBlockPtrT>::ChunkSList *
AllocPoolT<MemBlockPtrT>::getAlloc(SizeClassT sc)
{
    ChunkSList * csl(NULL);
    typename ChunkSList::AtomicHeadPtr & full = _scList[sc]._full;
    while ((csl = ChunkSList::linkOut(full)) == NULL) {
        Guard sync(_mutex);
        if (full.load(std::memory_order_relaxed)._ptr == NULL) {
            ChunkSList * ncsl(malloc(sync, sc));
            if (ncsl) {
                ChunkSList::linkInList(full, ncsl);
            } else {
                return NULL;
            }
        }
        USE_STAT2(_stat[sc]._getAlloc.fetch_add(1, std::memory_order_relaxed));
    }
    PARANOID_CHECK1( if (csl->empty() || (csl->count() > ChunkSList::NumBlocks)) { *(int*)0 = 0; } );
    return csl;
}

template <typename MemBlockPtrT>
typename AllocPoolT<MemBlockPtrT>::ChunkSList *
AllocPoolT<MemBlockPtrT>::getFree(SizeClassT sc, size_t UNUSED(minBlocks))
{
    ChunkSList * csl = getFree(sc);
    USE_STAT2(_stat[sc]._getFree.fetch_add(1, std::memory_order_relaxed));
    return csl;
}

template <typename MemBlockPtrT>
typename AllocPoolT<MemBlockPtrT>::ChunkSList *
AllocPoolT<MemBlockPtrT>::exchangeFree(SizeClassT sc, typename AllocPoolT<MemBlockPtrT>::ChunkSList * csl)
{
    PARANOID_CHECK1( if (csl->empty() || (csl->count() > ChunkSList::NumBlocks)) { *(int*)0 = 0; } );
    AllocFree & af = _scList[sc];
    ChunkSList::linkIn(af._full, csl, csl);
    ChunkSList *ncsl = getFree(sc);
    USE_STAT2(_stat[sc]._exchangeFree.fetch_add(1, std::memory_order_relaxed));
    return ncsl;
}

template <typename MemBlockPtrT>
typename AllocPoolT<MemBlockPtrT>::ChunkSList *
AllocPoolT<MemBlockPtrT>::exchangeAlloc(SizeClassT sc, typename AllocPoolT<MemBlockPtrT>::ChunkSList * csl)
{
    PARANOID_CHECK1( if ( ! csl->empty()) { *(int*)0 = 0; } );
    AllocFree & af = _scList[sc];
    ChunkSList::linkIn(af._empty, csl, csl);
    ChunkSList * ncsl = getAlloc(sc);
    USE_STAT2(_stat[sc]._exchangeAlloc.fetch_add(1, std::memory_order_relaxed));
    PARANOID_CHECK1( if (ncsl->empty() || (ncsl->count() > ChunkSList::NumBlocks)) { *(int*)0 = 0; } );
    return ncsl;
}

template <typename MemBlockPtrT>
typename AllocPoolT<MemBlockPtrT>::ChunkSList *
AllocPoolT<MemBlockPtrT>::exactAlloc(size_t exactSize, SizeClassT sc,
                                     typename AllocPoolT<MemBlockPtrT>::ChunkSList * csl)
{
    size_t adjustedSize((( exactSize + (_alwaysReuseLimit - 1))/_alwaysReuseLimit)*_alwaysReuseLimit);
    void *exactBlock = _dataSegment.getBlock(adjustedSize, sc);
    MemBlockPtrT mem(exactBlock, MemBlockPtrT::unAdjustSize(adjustedSize));
    csl->add(mem);
    ChunkSList * ncsl = csl;
    USE_STAT2(_stat[sc]._exactAlloc.fetch_add(1, std::memory_order_relaxed));
    mem.logBigBlock(exactSize, mem.adjustSize(exactSize), MemBlockPtrT::classSize(sc));
    PARANOID_CHECK1( if (ncsl->empty() || (ncsl->count() > ChunkSList::NumBlocks)) { *(int*)0 = 0; } );
    return ncsl;
}

template <typename MemBlockPtrT>
typename AllocPoolT<MemBlockPtrT>::ChunkSList *
AllocPoolT<MemBlockPtrT>::returnMemory(SizeClassT sc,
                                       typename AllocPoolT<MemBlockPtrT>::ChunkSList * csl)
{
    ChunkSList * completelyEmpty(NULL);
#if 0
    completelyEmpty = exchangeFree(sc, csl);
#else
    for(; !csl->empty(); ) {
        MemBlockPtrT mem;
        csl->sub(mem);
        mem.logBigBlock(mem.size(), mem.adjustSize(mem.size()), MemBlockPtrT::classSize(sc));
        _dataSegment.returnBlock(mem.rawPtr());
    }
    completelyEmpty = csl;
#endif
    USE_STAT2(_stat[sc]._return.fetch_add(1, std::memory_order_relaxed));
    return completelyEmpty;
}

template <typename MemBlockPtrT>
typename AllocPoolT<MemBlockPtrT>::ChunkSList *
AllocPoolT<MemBlockPtrT>::malloc(const Guard & guard, SizeClassT sc)
{
    const size_t numShifts =
        (sc <= MemBlockPtrT::SizeClassSpan) ? (MemBlockPtrT::SizeClassSpan - sc) : 0;
    size_t numBlocks = 1 << numShifts;
    const size_t cs(MemBlockPtrT::classSize(sc));
    size_t blockSize = cs * numBlocks;
    void * block = _dataSegment.getBlock(blockSize, sc);
    ChunkSList * csl(NULL);
    if (block != NULL) {
        numBlocks = (blockSize + cs - 1)/cs;
        const size_t blocksPerChunk(std::max(1, std::min(int(ChunkSList::NumBlocks),
                                                         int(_threadCacheLimit >> (MemBlockPtrT::MinClassSize + sc)))));

        const size_t numChunks = (numBlocks+(blocksPerChunk-1))/blocksPerChunk;
        csl = getChunks(guard, numChunks);
        if (csl != NULL) {
            char *first = (char *) block;
            const size_t itemSize = cs;
            size_t numItems(0);
            const size_t maxItems(blockSize/itemSize);
            ChunkSList * curr = csl;
            for ( ; curr->getNext() && (numItems < maxItems); curr = curr->getNext()) {
                PARANOID_CHECK1( if ( ! curr->empty()) { *(int*)0 = 0; } );
                numItems += curr->fill(first + numItems*itemSize, sc, blocksPerChunk);
            }
            if (numItems < maxItems) {
                PARANOID_CHECK1( if ( ! curr->empty()) { *(int*)0 = 0; } );
                PARANOID_CHECK1( if (numItems + blocksPerChunk < maxItems) { *(int*)1 = 1; } );
                numItems += curr->fill(first + numItems*itemSize, sc, maxItems - numItems);
            }
            // Can not add empty objects to list
            PARANOID_CHECK1( if (curr->empty()) { *(int*)0 = 0; } );
            // There must not be empty objects in list.
            PARANOID_CHECK1( if (curr->getNext()) { *(int*)1 = 1; } );
        }
    }
    PARANOID_CHECK1( for (ChunkSList * c(csl); c; c = c->getNext()) { if (c->empty()) { *(int*)1 = 1; } } );
    USE_STAT2(_stat[sc]._malloc.fetch_add(1, std::memory_order_relaxed));
    return csl;
}

template <typename MemBlockPtrT>
typename AllocPoolT<MemBlockPtrT>::ChunkSList *
AllocPoolT<MemBlockPtrT>::getChunks(const Guard & guard, size_t numChunks)
{
    ChunkSList * csl(_chunkPool);
    ChunkSList * prev(csl);
    bool enough(true);
    for (size_t i=0; enough && (i < numChunks); i++, csl = csl->getNext()) {
        if (csl == NULL) {
            csl = allocChunkList(guard);
            enough = (csl != NULL);
            if (prev) {
                prev->setNext(csl);
            } else {
                _chunkPool = csl;
            }
        }
        prev = csl;
    }
    if (enough) {
        csl = _chunkPool;
        _chunkPool = prev->getNext();
        prev->setNext(NULL);
    } else {
        csl = NULL;
    }
    USE_STAT2(_getChunks.fetch_add(1, std::memory_order_relaxed));
    USE_STAT2(_getChunksSum.fetch_add(numChunks, std::memory_order_relaxed));
    PARANOID_CHECK1( for (ChunkSList * c(csl); c; c = c->getNext()) { if ( ! c->empty()) { *(int*)1 = 1; } } );
    return csl;
}

template <typename MemBlockPtrT>
typename AllocPoolT<MemBlockPtrT>::ChunkSList *
AllocPoolT<MemBlockPtrT>::allocChunkList(const Guard & guard)
{
    (void) guard;
    size_t blockSize(sizeof(ChunkSList)*0x2000);
    void * block = _dataSegment.getBlock(blockSize, _dataSegment.SYSTEM_BLOCK);
    ChunkSList * newList(NULL);
    if (block != NULL) {
        size_t chunksInBlock(blockSize/sizeof(ChunkSList));
        newList = new (block) ChunkSList[chunksInBlock];
        for (size_t j=0; j < (chunksInBlock-1); j++) {
            newList[j].setNext(newList+j+1);
        }
        newList[chunksInBlock-1].setNext(NULL);
    }
    USE_STAT2(_allocChunkList.fetch_add(1, std::memory_order_relaxed));
    return newList;
}

template <typename MemBlockPtrT>
void AllocPoolT<MemBlockPtrT>::info(FILE * os, size_t level)
{
    if (level > 0) {
        fprintf(os, "GlobalPool getChunks(%ld, %ld) allocChunksList(%ld):\n",
                _getChunks.load(), _getChunksSum.load(), _allocChunkList.load());
        for (size_t i = 0; i < NELEMS(_stat); i++) {
            const Stat & s = _stat[i];
            if (s.isUsed()) {
                fprintf(os, "SC %2ld(%10ld) GetAlloc(%6ld) GetFree(%6ld) "
                            "ExChangeAlloc(%6ld) ExChangeFree(%6ld) ExactAlloc(%6ld) "
                            "Returned(%6ld) Malloc(%6ld)\n",
                            i, MemBlockPtrT::classSize(i), s._getAlloc.load(), s._getFree.load(),
                            s._exchangeAlloc.load(), s._exchangeFree.load(), s._exactAlloc.load(),
                            s._return.load(), s._malloc.load());
            }
        }
    }
}

}
