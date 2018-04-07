// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "lid_hold_list.h"
#include "lidstatevector.h"
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/queryeval/blueprint.h>

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
    uint32_t                    _numActiveLids;

public:
    LidAllocator(uint32_t size,
                 uint32_t capacity,
                 vespalib::GenerationHolder &genHolder);
    ~LidAllocator();

    DocId getFreeLid(DocId lidLimit);
    DocId peekFreeLid(DocId lidLimit);
    void ensureSpace(uint32_t newSize,
                     uint32_t newCapacity);
    void registerLid(DocId lid) { _usedLids.setBit(lid); }
    void unregisterLid(DocId lid);
    size_t getUsedLidsSize() const;
    void trimHoldLists(generation_t firstUsed);
    void moveLidBegin(DocId fromLid, DocId toLid);
    void moveLidEnd(DocId fromLid, DocId toLid);
    void holdLid(DocId lid, DocId lidLimit, generation_t currentGeneration);
    void holdLids(const std::vector<DocId> &lids, DocId lidLimit,
                  generation_t currentGeneration);
    bool holdLidOK(DocId lid, DocId lidLimit) const;
    void constructFreeList(DocId lidLimit);
    search::queryeval::Blueprint::UP createWhiteListBlueprint(uint32_t docIdLimit) const;
    void updateActiveLids(DocId lid, bool active);
    void clearDocs(DocId lidLow, DocId lidLimit);
    void shrinkLidSpace(DocId committedDocIdLimit);
    uint32_t getNumUsedLids() const;
    uint32_t getNumActiveLids() const {
        return _numActiveLids;
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
        return (lid < _usedLids.size() && _usedLids.testBit(lid));
    }
    DocId getLowestFreeLid() const {
        return _freeLids.getLowest();
    }
    DocId getHighestUsedLid() const {
        return _usedLids.getHighest();
    }

    const search::GrowableBitVector &getActiveLids() const { return _activeLids.getBitVector(); }
};

}
