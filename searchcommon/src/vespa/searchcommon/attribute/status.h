// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <atomic>

namespace search::attribute {

class Status
{
public:
    Status();
    Status(const Status& rhs);
    Status& operator=(const Status& rhs);

    void updateStatistics(uint64_t numValues, uint64_t numUniqueValue, uint64_t allocated,
                          uint64_t used, uint64_t dead, uint64_t onHold);

    uint64_t getNumDocs()                  const { return _numDocs; }
    uint64_t getNumValues()                const { return _numValues; }
    uint64_t getNumUniqueValues()          const { return _numUniqueValues; }
    uint64_t getAllocated()                const { return _allocated; }
    uint64_t getUsed()                     const { return _used; }
    uint64_t getDead()                     const { return _dead; }
    uint64_t getOnHold()                   const { return _onHold; }
    uint64_t getOnHoldMax()                const { return _onHoldMax; }
    // This might be accessed from other threads than the writer thread.
    uint64_t getLastSyncToken()            const { return _lastSyncToken.load(std::memory_order_relaxed); }
    uint64_t getUpdateCount()              const { return _updates; }
    uint64_t getNonIdempotentUpdateCount() const { return _nonIdempotentUpdates; }
    uint32_t getBitVectors() const { return _bitVectors; }

    void setNumDocs(uint64_t v)                  { _numDocs = v; }
    void incNumDocs()                            { ++_numDocs; }
    void setLastSyncToken(uint64_t v)            { _lastSyncToken.store(v, std::memory_order_relaxed); }
    void incUpdates(uint64_t v=1)                { _updates += v; }
    void incNonIdempotentUpdates(uint64_t v = 1) { _nonIdempotentUpdates += v; }
    void incBitVectors() { ++_bitVectors; }
    void decBitVectors() { --_bitVectors; }

    static vespalib::string
    createName(vespalib::stringref index, vespalib::stringref attr);
private:
    uint64_t _numDocs;
    uint64_t _numValues;
    uint64_t _numUniqueValues;
    uint64_t _allocated;
    uint64_t _used;
    uint64_t _dead;
    uint64_t _unused;
    uint64_t _onHold;
    uint64_t _onHoldMax;
    std::atomic<uint64_t> _lastSyncToken;
    uint64_t _updates;
    uint64_t _nonIdempotentUpdates;
    uint32_t _bitVectors;
};

}
