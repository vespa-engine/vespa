// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespamalloc/malloc/datasegment.h>

namespace vespamalloc {

template<typename MemBlockPtrT>
DataSegment<MemBlockPtrT>::~DataSegment() = default;

#define INIT_LOG_LIMIT 0x400000000ul // 16G

template<typename MemBlockPtrT>
DataSegment<MemBlockPtrT>::DataSegment() :
    _osMemory(BlockSize),
    _noMemLogLevel(1),
    _bigSegmentLogLevel(0),
    _bigIncrement (0x4000000),
    _allocs2Show (8),
    _unmapSize(0x100000),
    _nextLogLimit(INIT_LOG_LIMIT),
    _partialExtension(0),
    _mutex(),
    _freeList(_blockList),
    _unMappedList(_blockList)
{
    size_t wanted(0x1000000000ul); //64G
    void * everything = _osMemory.reserve(wanted);
    if (everything) {
        for (size_t i = blockId(everything), m = blockId(everything)+(wanted/BlockSize); i < m; i++) {
            if (i > BlockCount) {
                abort();
            }
            _blockList[i].sizeClass(UNUSED_BLOCK);
            _blockList[i].freeChainLength(m-i);
        }
        _freeList.add(blockId(everything));
    }
    _nextLogLimit = std::max(size_t(end()) + _nextLogLimit, _nextLogLimit);
}

template<typename MemBlockPtrT>
void * DataSegment<MemBlockPtrT>::getBlock(size_t & oldBlockSize, SizeClassT sc)
{
    const size_t minBlockSize = std::max(size_t(BlockSize), _osMemory.getMinBlockSize());
    oldBlockSize = ((oldBlockSize + (minBlockSize-1))/minBlockSize)*minBlockSize;
    size_t numBlocks((oldBlockSize + (BlockSize-1))/BlockSize);
    size_t blockSize = BlockSize * numBlocks;
    void * newBlock(nullptr);
    {
        Guard sync(_mutex);
        newBlock = _freeList.sub(numBlocks);
        if ( newBlock == nullptr ) {
            newBlock = _unMappedList.sub(numBlocks);
            if ( newBlock == nullptr ) {
                size_t nextBlock(blockId(end()));
                size_t startBlock = _freeList.lastBlock(nextBlock);
                if (startBlock) {
                    size_t adjustedBlockSize = blockSize - BlockSize*(nextBlock-startBlock);
                    newBlock = _osMemory.get(adjustedBlockSize);
                    if (newBlock != nullptr) {
                        assert (newBlock == fromBlockId(nextBlock));
                        _freeList.removeLastBlock();
                        newBlock = fromBlockId(startBlock);
                        _partialExtension++;
                    }
                } else {
                    newBlock = _osMemory.get(blockSize);
                }
            } else {
                bool result(_osMemory.reclaim(newBlock, blockSize));
                assert (result);
                (void) result;
            }
        } else {
            DEBUG(fprintf(stderr, "Reuse segment %p(%d, %d)\n", newBlock, sc, numBlocks));
        }
    }
    if (newBlock == (void *) -1) {
        newBlock = nullptr;
        blockSize = 0;
    } else if (newBlock == nullptr) {
        blockSize = 0;
    } else {
        assert(blockId(newBlock)+numBlocks < BlockCount);
        // assumes _osMemory.get will always return a value that does not make
        // "i" overflow the _blockList array; this will break when hitting the
        // 2T address space boundary.
        for (size_t i = blockId(newBlock), m = blockId(newBlock)+numBlocks; i < m; i++) {
            _blockList[i].sizeClass(sc);
            _blockList[i].freeChainLength(m-i);
            _blockList[i].realNumBlocks(m-i);
        }
    }
    oldBlockSize = blockSize;
    if (newBlock == nullptr) {
        static int recurse = 0;
        if (recurse++ == 0) {
            perror("Failed extending datasegment: ");
            assert(false);
            MemBlockPtrT::dumpInfo(_noMemLogLevel);
            sleep(2);
        }
        return nullptr;
    }
    checkAndLogBigSegment();
    return newBlock;
}

template<typename MemBlockPtrT>
void DataSegment<MemBlockPtrT>::checkAndLogBigSegment()
{
    if (size_t(end()) >= _nextLogLimit) {
        fprintf(stderr, "Datasegment is growing ! Start:%p - End:%p : nextLogLimit = %lx\n", start(), end(), _nextLogLimit);
        _nextLogLimit = ((size_t(end()) + _bigIncrement)/_bigIncrement)*_bigIncrement;
        static int recurse = 0;
        if (recurse++ == 0) {
            if (_bigSegmentLogLevel > 0) {
                MemBlockPtrT::dumpInfo(_bigSegmentLogLevel);
            }
        }
        recurse--;
    }
}

template<typename MemBlockPtrT>
void DataSegment<MemBlockPtrT>::returnBlock(void *ptr)
{
    size_t bId(blockId(ptr));
    SizeClassT sc =  _blockList[bId].sizeClass();
    size_t bsz = MemBlockPtrT::classSize(sc);
    if (bsz >= BlockSize) {
        size_t numBlocks = bsz / BlockSize;
        if (numBlocks > _blockList[bId].realNumBlocks()) {
            numBlocks = _blockList[bId].realNumBlocks();
        }
        assert(_blockList[bId].freeChainLength() >= numBlocks);
        if ((_unmapSize < bsz) && _osMemory.release(ptr, numBlocks*BlockSize)) {
            for(size_t i=0; i < numBlocks; i++) {
                BlockT & b = _blockList[bId + i];
                b.sizeClass(UNMAPPED_BLOCK);
                b.freeChainLength(numBlocks - i);
            }
            {
                Guard sync(_mutex);
                _unMappedList.add(bId);
            }
        } else {
            for(size_t i=0; i < numBlocks; i++) {
                BlockT & b = _blockList[bId + i];
                b.sizeClass(FREE_BLOCK);
                b.freeChainLength(numBlocks - i);
            }
            {
                Guard sync(_mutex);
                _freeList.add(bId);
            }
        }
    }
}

namespace {

std::vector<uint32_t>
createHistogram(bool allThreads, uint32_t maxThreads) {
    if (allThreads) {
        return std::vector<uint32_t>(maxThreads, 0);
    }
    return std::vector<uint32_t>();
}

}
template<typename MemBlockPtrT>
size_t DataSegment<MemBlockPtrT>::infoThread(FILE * os, int level, uint32_t thread, SizeClassT sct, uint32_t maxThreadId) const
{
    using CallGraphLT = CallGraph<typename MemBlockPtrT::Stack, 0x10000, Index>;
    bool allThreads(thread == 0);
    size_t usedCount(0);
    size_t checkedCount(0);
    size_t allocatedCount(0);
    size_t notAccounted(0);
    size_t invalidCallStacks(0);
    std::unique_ptr<CallGraphLT> callGraph = std::make_unique<CallGraphLT>();
    std::vector<uint32_t> threadHistogram = createHistogram(allThreads, maxThreadId);
    for (size_t i=0; i <  NELEMS(_blockList); ) {
        const BlockT & b = _blockList[i];
        SizeClassT sc = b.sizeClass();
        if (sc == sct) {
            size_t sz(MemBlockPtrT::classSize(sc));
            size_t numB(b.freeChainLength());
            for(char *m((char *)(fromBlockId(i))), *em((char*)(fromBlockId(i+numB))); (m + sz) <= em; m += sz) {
                MemBlockPtrT mem(m,0,false);
                checkedCount++;
                if (mem.allocated()) {
                    allocatedCount++;
                    if (allThreads || (mem.threadId() == thread)) {
                        usedCount++;
                        if (mem.threadId() < threadHistogram.size()) {
                            threadHistogram[mem.threadId()]++;
                        }
                        if (usedCount < _allocs2Show) {
                            mem.info(os, level);
                        }
                        if (mem.callStackLen() && mem.callStack()[0].valid()) {
                            size_t csl(mem.callStackLen());
                            for (size_t j(0); j < csl; j++) {
                                if ( ! mem.callStack()[j].valid()) {
                                    csl = j;
                                }
                            }
                            if ( ! callGraph->addStack(mem.callStack(), csl)) {
                                notAccounted++;
                            }
                        } else {
                            if (mem.callStackLen()) {
                                invalidCallStacks++;
                            }
                        }
                    }
                }
            }
            i += numB;
        } else {
            i++;
        }
    }
    if (checkedCount == 0) {
        return 0;
    }

    fprintf(os, "\nCallTree SC %d(Checked=%ld, GlobalAlloc=%ld(%ld%%)," "By%sAlloc=%ld(%2.2f%%) NotAccountedDue2FullGraph=%ld InvalidCallStacks=%ld:\n",
            sct, checkedCount, allocatedCount, checkedCount ? allocatedCount*100/checkedCount : 0,
            allThreads ? "Us" : "Me",
            usedCount, checkedCount ? static_cast<double>(usedCount*100)/checkedCount : 0.0, notAccounted, invalidCallStacks);
    if ( ! callGraph->empty()) {
        Aggregator agg;
        DumpGraph<typename CallGraphLT::Node> dump(&agg, "{ ", " }");
        callGraph->traverseDepth(dump);;
        asciistream ost;
        ost << agg;
        fprintf(os, "%s\n", ost.c_str());
    }
    if ( !threadHistogram.empty()) {
        uint32_t nonZeroCount(0);
        for (uint32_t i(0); i < threadHistogram.size(); i++) {
            if (threadHistogram[i] > 0) {
                nonZeroCount++;
            }
        }
        using Pair = std::pair<uint32_t, uint32_t>;
        std::vector<Pair> orderedHisto;
        orderedHisto.reserve(nonZeroCount);
        for (uint32_t i(0); i < threadHistogram.size(); i++) {
            if (threadHistogram[i] > 0) {
                orderedHisto.emplace_back(i, threadHistogram[i]);
            }
        }
        std::sort(orderedHisto.begin(), orderedHisto.end(), [](const Pair & a, const Pair & b) { return a.second > b.second;});
        fprintf(os, "ThreadHistogram SC %d: [", sct);

        bool first(true);
        for (const Pair & entry : orderedHisto) {
            if ( !first) {
                fprintf(os, ", ");
            }
            fprintf(os, "{%u, %u}", entry.first, entry.second);
            first = false;
        }
        fprintf(os, " ]");
    }
    return usedCount;
}

template<typename MemBlockPtrT>
void DataSegment<MemBlockPtrT>::info(FILE * os, size_t level)
{
    fprintf(os, "Start at %p, End at %p(%p) size(%ld) partialExtension(%ld) NextLogLimit(%lx) logLevel(%ld)\n",
            _osMemory.getStart(), _osMemory.getEnd(), sbrk(0), dataSize(), _partialExtension, _nextLogLimit, level);
    size_t numFreeBlocks(0), numAllocatedBlocks(0);
    {
        // Guard sync(_mutex);
        numFreeBlocks = _freeList.info(os, level);
        _unMappedList.info(os, level);
    }
    if (level >= 1) {
#ifdef PRINT_ALOT
        SizeClassT oldSc(-17);
        size_t oldChainLength(0);
#endif
        size_t scTable[32+NUM_ADMIN_CLASSES];
        memset(scTable, 0, sizeof(scTable));
        for (size_t i=0; (i < NELEMS(_blockList)) && ((i*BlockSize) < dataSize()); i++) {
            BlockT & b = _blockList[i];
#ifdef PRINT_ALOT
            if ((b.sizeClass() != oldSc)
                || ((oldChainLength < (b.freeChainLength()+1))
                    && b.freeChainLength()))
            {
                scTable[b.sizeClass()+NUM_ADMIN_CLASSES] += b.freeChainLength();
                oldSc = b.sizeClass();
                if (level & 0x2) {
                    fprintf(os, "Block %d at address %p with chainLength %d "
                            "freeCount %d sizeClass %d and size %d\n",
                            i, fromBlockId(i), b.freeChainLength(), b.freeCount(),
                            b.sizeClass(), classSize(b.sizeClass()));
                }
            }
            oldChainLength = b.freeChainLength();
#else
            scTable[b.sizeClass()+NUM_ADMIN_CLASSES]++;
#endif
        }
        size_t numAdminBlocks(0);
        for(size_t i=0; i < NUM_ADMIN_CLASSES; i++) {
            if (scTable[i] != 0ul) {
                numAllocatedBlocks += scTable[i];
                numAdminBlocks += scTable[i];
                fprintf(os, "SizeClass %2ld(%s) has %5ld blocks with %10lu bytes\n",
                        i-NUM_ADMIN_CLASSES, getAdminClassName(i-NUM_ADMIN_CLASSES), scTable[i], scTable[i]*BlockSize);
            }
        }
        for(size_t i=NUM_ADMIN_CLASSES; i < NELEMS(scTable); i++) {
            if (scTable[i] != 0ul) {
                numAllocatedBlocks += scTable[i];
                fprintf(os, "SizeClass %2ld has %5ld blocks with %10lu bytes\n",
                            i-NUM_ADMIN_CLASSES, scTable[i], scTable[i]*BlockSize);
            }
        }
        size_t total(dataSize()/BlockSize);
        fprintf(os, "Usage: Total=%ld(100%%), admin=%ld(%ld%%), unused=%ld(%ld%%), allocated=%ld(%ld%%)\n",
                total*BlockSize,
                numAdminBlocks*BlockSize, numAdminBlocks*100/total,
                numFreeBlocks*BlockSize, numFreeBlocks*100/total,
                (numAllocatedBlocks-numAdminBlocks)*BlockSize, (numAllocatedBlocks-numAdminBlocks)*100/total);
    }
}

template<typename MemBlockPtrT>
template <int MaxCount>
DataSegment<MemBlockPtrT>::FreeListT<MaxCount>::FreeListT(BlockT * blockList) :
    _blockList(blockList),
    _count(0)
{
    for (size_t i = 0; i < NELEMS(_freeStartIndex); i++) {
        _freeStartIndex[i] = -1;
    }
}

template<typename MemBlockPtrT>
template <int MaxCount>
void DataSegment<MemBlockPtrT>::FreeListT<MaxCount>::add(size_t startIndex)
{
    size_t i(0);
    size_t numBlocks(_blockList[startIndex].freeChainLength());
    for (i=0; (i < _count) && (_freeStartIndex[i] < startIndex); i++) { }
    size_t prevIndex(0), nextIndex(0);
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
        size_t oldNextCount = next->freeChainLength();
        next = & _blockList[startIndex];
        next->freeChainLength(oldNextCount + numBlocks);
    } else {
        // Insert.
        for(size_t j=0; j < (_count-i); j++) {
            _freeStartIndex[_count-j] = _freeStartIndex[_count-j-1];
        }
        _count++;
        _freeStartIndex[i] = startIndex;
    }

    if (prev && next && (prevIndex + prev->freeChainLength() == nextIndex)) {
        prev->freeChainLength(prev->freeChainLength() + next->freeChainLength());
        _count--;
        for(size_t j=i; j < _count; j++) {
            _freeStartIndex[j] = _freeStartIndex[j+1];
        }
        _freeStartIndex[_count] = -1;
    }
}

template<typename MemBlockPtrT>
template <int MaxCount>
void * DataSegment<MemBlockPtrT>::FreeListT<MaxCount>::sub(size_t numBlocks)
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

template<typename MemBlockPtrT>
template <int MaxCount>
size_t DataSegment<MemBlockPtrT>::FreeListT<MaxCount>::lastBlock(size_t nextBlock)
{
    size_t lastIndex(0);
    if (_count > 0) {
        size_t index(_freeStartIndex[_count-1]);
        BlockT & b = _blockList[index];
        if (index + b.freeChainLength() == nextBlock) {
            lastIndex = index;
        }
    }
    return lastIndex;
}

template<typename MemBlockPtrT>
template <int MaxCount>
size_t DataSegment<MemBlockPtrT>::FreeListT<MaxCount>::info(FILE * os, int UNUSED(level))
{
    size_t freeBlockCount(0);
    for (size_t i=0; i < _count; i++) {
        size_t index(_freeStartIndex[i]);
        const BlockT & b = _blockList[index];
        freeBlockCount += b.freeChainLength();
        fprintf(os, "Free #%3ld block #%5ld chainlength %5d size %10lu\n",
                i, index, b.freeChainLength(), size_t(b.freeChainLength())*BlockSize);
    }
    return freeBlockCount;
}

template<typename MemBlockPtrT>
template <int MaxCount>
void * DataSegment<MemBlockPtrT>::FreeListT<MaxCount>::linkOut(size_t findex, size_t left)
{
    size_t index(_freeStartIndex[findex]);
    BlockT & b = _blockList[index];
    size_t startIndex = index + left;
    void *block = fromBlockId(startIndex);
    if (left > 0) {
        b.freeChainLength(left);
    } else {
        _count--;
        for(size_t j=findex; j < (_count); j++) {
            _freeStartIndex[j] = _freeStartIndex[j+1];
        }
        _freeStartIndex[_count] = -1;
    }
    return block;
}

}
