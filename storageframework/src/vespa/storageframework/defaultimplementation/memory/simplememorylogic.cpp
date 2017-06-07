// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simplememorylogic.h"
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".memory.logic.simple");

namespace storage::framework::defaultimplementation {

SimpleMemoryLogic::SimpleMemoryLogic(Clock& c, uint64_t maxMemory)
    : _cacheThreshold(0.98),
      _nonCacheThreshold(0.8),
      _state(c, maxMemory),
      _reducers()
{
    LOG(debug, "Setup simple memory logic with max memory of %" PRIu64 " bytes", maxMemory);
}

SimpleMemoryLogic::~SimpleMemoryLogic()
{
}

void
SimpleMemoryLogic::setMaximumMemoryUsage(uint64_t max)
{
    vespalib::LockGuard lock(_stateLock);
    _state.setMaximumMemoryUsage(max);
}

void
SimpleMemoryLogic::getState(MemoryState& state, bool resetMax) {
    vespalib::LockGuard lock(_stateLock);
    state = _state;
    if (resetMax) _state.resetMax();
}

MemoryToken::UP
SimpleMemoryLogic::allocate(const MemoryAllocationType& type,
                            uint8_t priority,
                            ReduceMemoryUsageInterface* reducer)
{
    MemoryTokenImpl::UP token(
            new MemoryTokenImpl(*this, type, 0, priority, reducer));
    if (reducer != 0) {
        vespalib::LockGuard lock(_stateLock);
        _reducers.push_back(Reducer(*token, *reducer));
    }
    return std::move(token);
}

bool
SimpleMemoryLogic::resize(MemoryToken& tok, uint64_t min, uint64_t max,
                          uint32_t allocationCounts)
{
    vespalib::LockGuard lock(_stateLock);
    MemoryTokenImpl& token(static_cast<MemoryTokenImpl&>(tok));
    LOG(spam, "Attempting to resize %s to size in the range %" PRIu64 " to "
               "%" PRIu64 ".", token.toString().c_str(), min, max);
    if (token.getSize() > max) { // Always safe to reduce size
        handleReduction(token, max, allocationCounts);
        return true;
    }
    // If not reducing size, calculate relative min/max values.
    uint64_t relMin = (min > token.getSize() ? min - token.getSize() : 0);
    uint64_t relMax = max - token.getSize();
    return resizeRelative(token, relMin, relMax, allocationCounts);
}

void
SimpleMemoryLogic::handleReduction(MemoryTokenImpl& token, uint64_t max,
                                   uint32_t allocationCounts)
{
    LOG(spam, "Reduzing size of token by %" PRIu64 ".",
               token.getSize() - max);
    _state.removeFromEntry(token.getType(), token.getSize() - max,
                           token.getPriority(), allocationCounts);
    setTokenSize(token, max);
}

bool
SimpleMemoryLogic::handleCacheMemoryRequest(
        MemoryTokenImpl& token, uint64_t min, uint64_t max,
        uint32_t allocationCounts)
{
    uint64_t usedSize(_state.getCurrentSnapshot().getUsedSize());
    uint64_t thresholdSize = uint64_t(getCacheThreshold()
                                      * _state.getTotalSize());
    uint64_t toAllocate(thresholdSize > usedSize
            ? std::min(thresholdSize - usedSize, max)
            : 0);
    bool forced = false;
    if (token.getType().isAllocationsForced() && toAllocate < min) {
        toAllocate = min;
        forced = true;
    }
    if (toAllocate < min) {
        LOG(spam, "We cannot give more memory to cache without going above "
                  "cache threshold (%" PRIu64 " B)", thresholdSize);
        _state.addToEntry(token.getType(), 0, token.getPriority(),
                          MemoryState::DENIED, false, allocationCounts);
        return false;
    }
    LOG(spam, "Giving %" PRIu64 " bytes of memory to cache. (Cache threshold "
              "is %" PRIu64 ", used size is %" PRIu64 ", %" PRIu64 " bytes were "
              "always allocated to the token and it wanted memory between %"
              PRIu64 " and %" PRIu64 ".",
        toAllocate, thresholdSize, usedSize, token.getSize(), min, max);
    _state.addToEntry(token.getType(), toAllocate, token.getPriority(),
                      static_cast<uint64_t>(toAllocate) >= max
                          ? MemoryState::GOT_MAX : MemoryState::GOT_MIN,
                      forced, allocationCounts);
    setTokenSize(token, token.getSize() + toAllocate);
    return true;
}

uint64_t
SimpleMemoryLogic::getMemorySizeFreeForPriority(uint8_t priority) const
{
    uint64_t usedSize(_state.getCurrentSnapshot().getUsedSizeIgnoringCache());
    uint64_t thresholdSize = uint64_t(getNonCacheThreshold(priority)
                                      * _state.getTotalSize());
    return (usedSize >= thresholdSize ? 0 : thresholdSize - usedSize);
}

bool
SimpleMemoryLogic::resizeRelative(
        MemoryTokenImpl& token, uint64_t min, uint64_t max,
        uint32_t allocationCounts)
{
    LOG(spam, "Relative resize change. Need another %zu-%zu byte of memory.",
        min, max);
    // If requester is cache, use cache threshold
    if (token.getType().isCache()) {
        return handleCacheMemoryRequest(token, min, max, allocationCounts);
    }
    // If we get here, requester is not cache.
    uint64_t usedSize(_state.getCurrentSnapshot().getUsedSizeIgnoringCache());
    uint64_t thresholdSize = uint64_t(getNonCacheThreshold(token.getPriority())
                                      * _state.getTotalSize());
    uint64_t toAllocate = 0;
    if (thresholdSize > usedSize) {
        toAllocate = std::min(max, thresholdSize - usedSize);
    }
    if (toAllocate < min) toAllocate = min;
    bool forced = false;
    if (usedSize + toAllocate > _state.getTotalSize()) {
        if (token.getType().isAllocationsForced()) {
            forced = true;
        } else {
            LOG(spam, "We cannot give more memory without going beyond max");
            _state.addToEntry(token.getType(), 0, token.getPriority(),
                              MemoryState::DENIED, false, allocationCounts);
            return false;
        }
    }
    // External load should not fill up too much
    if (usedSize + toAllocate > thresholdSize
        && token.getType().isExternalLoad()
        && !token.getType().isAllocationsForced())
    {
        LOG(spam, "Not giving external load memory beyond threshold.");
        _state.addToEntry(token.getType(), 0, token.getPriority(),
                          MemoryState::DENIED, false, allocationCounts);
        return false;
    }
    // If this puts us above max with cache, remove some cache.
    if (_state.getCurrentSnapshot().getUsedSize() + toAllocate
            > _state.getTotalSize())
    {
        uint64_t needed(_state.getCurrentSnapshot().getUsedSize()
                        + toAllocate - _state.getTotalSize());
        for (uint32_t i=0; i<_reducers.size(); ++i) {
            MemoryTokenImpl& rtoken(*_reducers[i]._token);
            uint64_t reduceBy(std::min(needed, rtoken.getSize()));
            uint64_t reduced(_reducers[i]._reducer->reduceMemoryConsumption(
                                rtoken, reduceBy));
            _state.removeFromEntry(rtoken.getType(), reduced,
                                   rtoken.getPriority(), 0);
            setTokenSize(rtoken, rtoken.getSize() - reduced);
            needed -= reduceBy;
            if (needed == 0) break;
            if (reduced < reduceBy) {
                LOG(debug, "Reducer refused to free the full %" PRIu64 " bytes "
                           "requested. %" PRIu64 " bytes reduced in token %s.",
                    reduceBy, reduced, rtoken.toString().c_str());
            }
        }
    }
    if (_state.getCurrentSnapshot().getUsedSize() + toAllocate
            > _state.getTotalSize())
    {
        LOGBP(debug, "Failed to free enough memory from cache. This puts us "
                     "above max memory.");
    }
    LOG(spam, "Giving %" PRIu64 " bytes of memory", toAllocate);
    _state.addToEntry(token.getType(), toAllocate, token.getPriority(),
                      static_cast<uint64_t>(toAllocate) >= max
                          ? MemoryState::GOT_MAX : MemoryState::GOT_MIN,
                      forced, allocationCounts);
    setTokenSize(token, token.getSize() + toAllocate);
    return true;
}

void
SimpleMemoryLogic::freeToken(MemoryTokenImpl& token)
{
    vespalib::LockGuard lock(_stateLock);
    _state.removeFromEntry(token.getType(), token.getSize(),
                           token.getPriority(), token.getAllocationCount());
    if (token.getReducer() != 0) {
        std::vector<Reducer> reducers;
        reducers.reserve(_reducers.size() - 1);
        for (uint32_t i=0; i<_reducers.size(); ++i) {
            if (_reducers[i]._token != &token) reducers.push_back(_reducers[i]);
        }
        assert(reducers.size() + 1 == _reducers.size());
        reducers.swap(_reducers);
    }
}

void
SimpleMemoryLogic::print(std::ostream& out, bool verbose,
                         const std::string& indent) const
{
    out << "SimpleMemoryLogic() {\n"
        << indent << "  ";
    vespalib::LockGuard lock(_stateLock);
    _state.print(out, verbose, indent + "  ");
}

}
