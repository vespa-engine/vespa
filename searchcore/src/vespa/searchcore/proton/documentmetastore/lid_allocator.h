// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "lid_hold_list.h"
#include "lidstatevector.h"
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <atomic>

namespace proton::documentmetastore {

/**
 * Class responsible for allocating lids and managing
 * which lids are used, active and free.
 */
class LidAllocator
{
private:
    typedef uint32_t DocId;
    typedef vespalib::GenerationHandler::generation_t generation_t;

    LidHoldList                 _holdLids;
    LidStateVector              _freeLids;
    LidStateVector              _usedLids;
    LidStateVector              _pendingHoldLids;
    bool                        _lidFreeListConstructed;
    LidStateVector              _activeLids;
    std::atomic<uint32_t>       _numActiveLids;

public:
    LidAllocator(uint32_t size,
                 uint32_t capacity,
                 vespalib::GenerationHolder &genHolder);
    ~LidAllocator();

    DocId getFreeLid(DocId lidLimit);
    DocId peekFreeLid(DocId lidLimit);
    void ensureSpace(uint32_t newSize, uint32_t newCapacity);
    void registerLid(DocId lid) { _usedLids.setBit(lid); }
    void unregisterLid(DocId lid);
    void unregister_lids(const std::vector<DocId>& lids);
    size_t getUsedLidsSize() const { return _usedLids.byteSize(); }
    void reclaim_memory(generation_t oldest_used_gen) {
        _holdLids.reclaim_memory(oldest_used_gen, _freeLids);
    }
    void moveLidBegin(DocId fromLid, DocId toLid);
    void moveLidEnd(DocId fromLid, DocId toLid);
    void holdLids(const std::vector<DocId> &lids, DocId lidLimit,
                  generation_t currentGeneration);
    bool holdLidOK(DocId lid, DocId lidLimit) const;
    void constructFreeList(DocId lidLimit);
    search::queryeval::Blueprint::UP createWhiteListBlueprint() const;
    void updateActiveLids(DocId lid, bool active);
    void clearDocs(DocId lidLow, DocId lidLimit);
    void shrinkLidSpace(DocId committedDocIdLimit);
    uint32_t getNumUsedLids() const { return _usedLids.count(); }
    uint32_t getNumActiveLids() const noexcept {
        return _numActiveLids.load(std::memory_order_relaxed);
    }
    void setFreeListConstructed() {
        _lidFreeListConstructed = true;
    }
    bool isFreeListConstructed() const {
        return _lidFreeListConstructed;
    }
    bool validButMaybeUnusedLid(DocId lid) const {
        return lid < _usedLids.size();
    }
    bool validLid(DocId lid) const {
        auto &vector = _usedLids.getBitVector();
        return (lid < vector.getSizeAcquire() && vector.testBitAcquire(lid));
    }
    bool validLid(DocId lid, uint32_t limit) const {
        return (lid < limit && _usedLids.testBitAcquire(lid));
    }
    DocId getLowestFreeLid() const {
        return _freeLids.getLowest();
    }
    DocId getHighestUsedLid() const {
        return _usedLids.getHighest();
    }

    const search::BitVector &getActiveLids() const { return _activeLids.getBitVector(); }
};

}
