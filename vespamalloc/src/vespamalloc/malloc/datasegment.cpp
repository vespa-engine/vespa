// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "datasegment.h"
#include <algorithm>
#include <unistd.h>

namespace vespamalloc::segment {

DataSegment::~DataSegment() = default;

#define INIT_LOG_LIMIT 0x400000000ul // 16G

DataSegment::DataSegment(const IHelper & helper) :
    _osMemory(BlockSize),
    _bigSegmentLogLevel(0),
    _bigIncrement (0x4000000),
    _allocs2Show (8),
    _unmapSize(0x100000),
    _nextLogLimit(INIT_LOG_LIMIT),
    _partialExtension(0),
    _helper(helper),
    _mutex(),
    _freeList(_blockList),
    _unMappedList(_blockList)
{
    size_t wanted(0x1000000000ul); //64G
    void * everything = _osMemory.reserve(wanted);
    if (everything) {
        for (BlockIdT i = blockId(everything), m = blockId(everything) + (wanted / BlockSize); i < m; i++) {
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

size_t
DataSegment::freeSize() const {
    return _freeList.numFreeBlocks() * BlockSize;
}

void * DataSegment::getBlock(size_t & oldBlockSize, SizeClassT sc)
{
    const size_t minBlockSize = std::max(BlockSize, _osMemory.getMinBlockSize());
    oldBlockSize = ((oldBlockSize + (minBlockSize-1))/minBlockSize)*minBlockSize;
    BlockIdT numBlocks((oldBlockSize + (BlockSize - 1)) / BlockSize);
    size_t blockSize = BlockSize * numBlocks;
    void * newBlock;
    {
        Guard sync(_mutex);
        newBlock = _freeList.sub(numBlocks);
        if ( newBlock == nullptr ) {
            newBlock = _unMappedList.sub(numBlocks);
            if ( newBlock == nullptr ) {
                BlockIdT nextBlock = blockId(end());
                BlockIdT startBlock = _freeList.lastBlock(nextBlock);
                if (startBlock) {
                    size_t adjustedBlockSize = blockSize - BlockSize*(nextBlock-startBlock);
                    newBlock = _osMemory.get(adjustedBlockSize);
                    if (newBlock != nullptr) {
                        ASSERT_STACKTRACE (newBlock == fromBlockId(nextBlock));
                        _freeList.removeLastBlock();
                        newBlock = fromBlockId(startBlock);
                        _partialExtension++;
                    }
                } else {
                    newBlock = _osMemory.get(blockSize);
                }
            } else {
                bool result(_osMemory.reclaim(newBlock, blockSize));
                ASSERT_STACKTRACE (result);
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
        ASSERT_STACKTRACE(blockId(newBlock)+numBlocks < BlockCount);
        // assumes _osMemory.get will always return a value that does not make
        // "i" overflow the _blockList array; this will break when hitting the
        // 2T address space boundary.
        for (BlockIdT i = blockId(newBlock), m = blockId(newBlock) + numBlocks; i < m; i++) {
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
            ASSERT_STACKTRACE(false);
        }
        return nullptr;
    }
    checkAndLogBigSegment();
    return newBlock;
}

void
DataSegment::checkAndLogBigSegment()
{
    if (size_t(end()) >= _nextLogLimit) {
        fprintf(stderr, "Datasegment is growing ! Start:%p - End:%p : nextLogLimit = %lx\n", start(), end(), _nextLogLimit);
        _nextLogLimit = ((size_t(end()) + _bigIncrement)/_bigIncrement)*_bigIncrement;
        static int recurse = 0;
        if (recurse++ == 0) {
            if (_bigSegmentLogLevel > 0) {
                _helper.dumpInfo(_bigSegmentLogLevel);
            }
        }
        recurse--;
    }
}

void
DataSegment::returnBlock(void *ptr)
{
    BlockIdT bId(blockId(ptr));
    SizeClassT sc =  _blockList[bId].sizeClass();
    size_t bsz = _helper.classSize(sc);
    if (bsz >= BlockSize) {
        BlockIdT numBlocks = bsz / BlockSize;
        if (numBlocks > _blockList[bId].realNumBlocks()) {
            numBlocks = _blockList[bId].realNumBlocks();
        }
        ASSERT_STACKTRACE(_blockList[bId].freeChainLength() >= numBlocks);
        if ((_unmapSize < bsz) && _osMemory.release(ptr, numBlocks*BlockSize)) {
            for(BlockIdT i=0; i < numBlocks; i++) {
                BlockT & b = _blockList[bId + i];
                b.sizeClass(UNMAPPED_BLOCK);
                b.freeChainLength(numBlocks - i);
            }
            {
                Guard sync(_mutex);
                _unMappedList.add(bId);
            }
        } else {
            for(BlockIdT i=0; i < numBlocks; i++) {
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

size_t DataSegment::infoThread(FILE * os, int level, uint32_t thread, SizeClassT sct, uint32_t maxThreadId) const
{
    using CallGraphLT = CallGraph<StackEntry, 0x10000, Index>;
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
            size_t sz = _helper.classSize(sc);
            size_t numB(b.freeChainLength());
            for(char *m((char *)(fromBlockId(i))), *em((char*)(fromBlockId(i+numB))); (m + sz) <= em; m += sz) {
                (void) m;
                (void) em;
                auto mem = _helper.createMemblockInfo(m);
                checkedCount++;
                if (mem->allocated()) {
                    allocatedCount++;
                    if (allThreads || (mem->threadId() == thread)) {
                        usedCount++;
                        if (mem->threadId() < threadHistogram.size()) {
                            threadHistogram[mem->threadId()]++;
                        }
                        if (usedCount < _allocs2Show) {
                            mem->info(os, level);
                        }
                        if (mem->callStackLen() && mem->callStack()[0].valid()) {
                            size_t csl(mem->callStackLen());
                            for (size_t j(0); j < csl; j++) {
                                if ( ! mem->callStack()[j].valid()) {
                                    csl = j;
                                }
                            }
                            if ( ! callGraph->addStack(mem->callStack(), csl)) {
                                notAccounted++;
                            }
                        } else {
                            if (mem->callStackLen()) {
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
    if (checkedCount == 0) return 0;

    fprintf(os, "\nCallTree SC %d(Checked=%ld, GlobalAlloc=%ld(%ld%%)," "By%sAlloc=%ld(%2.2f%%) NotAccountedDue2FullGraph=%ld InvalidCallStacks=%ld:\n",
            sct, checkedCount, allocatedCount, allocatedCount*100/checkedCount,
            allThreads ? "Us" : "Me",
            usedCount, static_cast<double>(usedCount*100)/checkedCount, notAccounted, invalidCallStacks);
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

void DataSegment::info(FILE * os, size_t level)
{
    fprintf(os, "Start at %p, End at %p(%p) size(%ld) partialExtension(%ld) NextLogLimit(%lx) logLevel(%ld)\n",
            _osMemory.getStart(), _osMemory.getEnd(), sbrk(0), dataSize(), _partialExtension, _nextLogLimit, level);
    size_t numAllocatedBlocks(0);
    size_t numFreeBlocks = _freeList.numFreeBlocks();
    _freeList.info(os);
    _unMappedList.info(os);
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

}
