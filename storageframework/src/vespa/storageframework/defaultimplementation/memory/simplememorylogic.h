// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * \class storage::SimpleMemoryLogic
 *
 * \brief Simple logic deciding who should get memory and how much.
 *
 * There is a cache threshold. By default 98%. Cache will always get memory up
 * till this fillrate.
 *
 * There is a non-cache threshold. Non-cache memory requesters will get maximum
 * memory until threshold is reached. If getting maximum memory would go beyond
 * the non-cache threshold, the requester will get enough memory to hit the
 * threshold (if more than minimum), or get the minimum memory asked for, if
 * that doesn't put usage above 100%.
 *
 * Usage above 100% is attempted avoided by freeing cache memory. If failing to
 * free enough memory, request will fail, or minimum will be get if allocation
 * is forced such that it cannot fail. In such a case, usage may go beyond 100%.
 */

#pragma once

#include "memorymanager.h"
#include "memorystate.h"
#include <vespa/vespalib/util/sync.h>

namespace storage {
namespace framework {
namespace defaultimplementation {

class SimpleMemoryLogic : public AllocationLogic
{
    float _cacheThreshold;
    float _nonCacheThreshold;
    vespalib::Lock _stateLock;
    MemoryState _state;
    struct Reducer {
        MemoryTokenImpl* _token;
        ReduceMemoryUsageInterface* _reducer;

        Reducer() : _token(0), _reducer(0) {}
        Reducer(MemoryTokenImpl& t,
                ReduceMemoryUsageInterface& r)
            : _token(&t), _reducer(&r) {}
    };
    std::vector<Reducer> _reducers;

protected:
    float getCacheThreshold() { return _cacheThreshold; }

        // Priority memory logic can override this to set a threshold based on
        // priority
    virtual float getNonCacheThreshold(uint8_t priority) const
        { (void) priority; return _nonCacheThreshold; }

public:
    typedef std::unique_ptr<SimpleMemoryLogic> UP;

    SimpleMemoryLogic(Clock&, uint64_t maxMemory);

    ~SimpleMemoryLogic();

    SimpleMemoryLogic& setMinJumpToUpdateMax(uint32_t bytes) {
        _state.setMinJumpToUpdateMax(bytes);
        return *this;
    }

    void setMaximumMemoryUsage(uint64_t max) override;

    void setCacheThreshold(float limit) { _cacheThreshold = limit; }
    void setNonCacheThreshold(float limit) { _nonCacheThreshold = limit; }

    MemoryState& getState() { return _state; } // Not threadsafe. Unit testing.
    void getState(MemoryState& state, bool resetMax) override;

    MemoryToken::UP allocate(const MemoryAllocationType&, uint8_t priority,
                             ReduceMemoryUsageInterface* = 0) override;
    bool resize(MemoryToken& token, uint64_t min, uint64_t max, uint32_t allocationCounts) override;

    void freeToken(MemoryTokenImpl& token) override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    virtual uint64_t getMemorySizeFreeForPriority(uint8_t priority) const override;

private:
    void handleReduction(MemoryTokenImpl&, uint64_t size,
                         uint32_t allocationCounts);
    bool resizeRelative(MemoryTokenImpl&, uint64_t min, uint64_t max,
                        uint32_t allocationCounts);
    bool handleCacheMemoryRequest(MemoryTokenImpl&, uint64_t min, uint64_t max,
                                  uint32_t allocationCounts);
};

} // defaultimplementation
} // framework
} // storage

