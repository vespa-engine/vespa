// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "datastore.h"

using vespalib::GenerationHeldBase;

namespace search {
namespace datastore {

namespace {

/*
 * Minimum dead bytes in active write buffer before switching to new
 * active write buffer even if another active buffer has more dead
 * bytes due to considering the active write buffer as too dead.
 */
constexpr size_t TOODEAD_SLACK = 0x4000u;

/*
 * Check if active write buffer is too dead for further use, i.e. if it
 * is likely to be the worst buffer at next compaction.  If so, filling it
 * up completely will be wasted work, as data will have to be moved again
 * rather soon.
 */
bool activeWriteBufferTooDead(const BufferState &state)
{
    size_t deadElems = state.getDeadElems();
    size_t deadBytes = deadElems * state.getClusterSize();
    return ((deadBytes >= TOODEAD_SLACK) && (deadElems * 2 >= state.size()));
}

}

DataStoreBase::FallbackHold::FallbackHold(size_t size,
        BufferState::Alloc &&buffer,
        size_t usedElems,
        BufferTypeBase *typeHandler,
        uint32_t typeId)
    : GenerationHeldBase(size),
      _buffer(std::move(buffer)),
      _usedElems(usedElems),
      _typeHandler(typeHandler),
      _typeId(typeId)
{
}


DataStoreBase::FallbackHold::~FallbackHold(void)
{
    _typeHandler->destroyElements(_buffer.get(), _usedElems);
}


class DataStoreBase::BufferHold : public GenerationHeldBase
{
    DataStoreBase &_dsb;
    uint32_t _bufferId;

public:
    BufferHold(size_t size,
               DataStoreBase &dsb,
               uint32_t bufferId)
        : GenerationHeldBase(size),
          _dsb(dsb),
          _bufferId(bufferId)
    {
    }

    virtual
    ~BufferHold(void)
    {
        _dsb.doneHoldBuffer(_bufferId);
    }
};


DataStoreBase::DataStoreBase(uint32_t numBuffers,
                             size_t maxClusters)
    : _buffers(numBuffers),
      _activeBufferIds(),
      _states(numBuffers),
      _typeHandlers(),
      _freeListLists(),
      _freeListsEnabled(false),
      _elemHold1List(),
      _elemHold2List(),
      _numBuffers(numBuffers),
      _maxClusters(maxClusters),
      _genHolder()
{
}


DataStoreBase::~DataStoreBase(void)
{
    disableFreeLists();

    assert(_elemHold1List.empty());
    assert(_elemHold2List.empty());
}


void
DataStoreBase::switchActiveBuffer(uint32_t typeId, size_t sizeNeeded)
{
    size_t activeBufferId = _activeBufferIds[typeId];
    do {
        // start using next buffer
        activeBufferId = nextBufferId(activeBufferId);
    } while (_states[activeBufferId]._state != BufferState::FREE);
    onActive(activeBufferId, typeId, sizeNeeded, _maxClusters);
    _activeBufferIds[typeId] = activeBufferId;
}


void
DataStoreBase::initActiveBuffers(void)
{
    uint32_t numTypes = _activeBufferIds.size();
    for (uint32_t typeId = 0; typeId < numTypes; ++typeId) {
        size_t activeBufferId = 0;
        while (_states[activeBufferId]._state != BufferState::FREE) {
            // start using next buffer
            activeBufferId = nextBufferId(activeBufferId);
        }
        onActive(activeBufferId, typeId, 0u, _maxClusters);
        _activeBufferIds[typeId] = activeBufferId;
    }
}


uint32_t
DataStoreBase::addType(BufferTypeBase *typeHandler)
{
    uint32_t typeId = _activeBufferIds.size();
    assert(typeId == _typeHandlers.size());
    _activeBufferIds.push_back(0);
    _typeHandlers.push_back(typeHandler);
    _freeListLists.push_back(BufferState::FreeListList());
    return typeId;
}


void
DataStoreBase::transferElemHoldList(generation_t generation)
{
    ElemHold2List &elemHold2List = _elemHold2List;
    for (const ElemHold1ListElem & elemHold1 : _elemHold1List) {
        elemHold2List.push_back(ElemHold2ListElem(elemHold1, generation));
    }
    _elemHold1List.clear();
}


void
DataStoreBase::transferHoldLists(generation_t generation)
{
    _genHolder.transferHoldLists(generation);
    if (hasElemHold1())
        transferElemHoldList(generation);
}


void
DataStoreBase::doneHoldBuffer(uint32_t bufferId)
{
    _states[bufferId].onFree(_buffers[bufferId]);
}


void
DataStoreBase::trimHoldLists(generation_t usedGen)
{
    trimElemHoldList(usedGen);	// Trim entries before trimming buffers

    _genHolder.trimHoldLists(usedGen);
}


void
DataStoreBase::clearHoldLists(void)
{
    transferElemHoldList(0);
    clearElemHoldList();
    _genHolder.clearHoldLists();
}


void
DataStoreBase::dropBuffers(void)
{
    uint32_t numBuffers = _buffers.size();
    for (uint32_t bufferId = 0; bufferId < numBuffers; ++bufferId) {
        _states[bufferId].dropBuffer(_buffers[bufferId]);
    }
    _genHolder.clearHoldLists();
}


MemoryUsage
DataStoreBase::getMemoryUsage(void) const
{
    MemStats stats = getMemStats();
    MemoryUsage usage;
    usage.setAllocatedBytes(stats._allocBytes);
    usage.setUsedBytes(stats._usedBytes);
    usage.setDeadBytes(stats._deadBytes);
    usage.setAllocatedBytesOnHold(stats._holdBytes);
    return usage;
}


void
DataStoreBase::holdBuffer(uint32_t bufferId)
{
    _states[bufferId].onHold();
    size_t holdBytes = 0u;	// getMemStats() still accounts held buffers
    GenerationHeldBase::UP hold(new BufferHold(holdBytes, *this, bufferId));
    _genHolder.hold(std::move(hold));
}


void
DataStoreBase::enableFreeLists(void)
{
    for (BufferState & bState : _states) {
        if (bState._state != BufferState::ACTIVE || bState.getCompacting())
            continue;
        bState.setFreeListList(&_freeListLists[bState._typeId]);
    }
    _freeListsEnabled = true;
}


void
DataStoreBase::disableFreeLists(void)
{
    for (BufferState & bState : _states) {
        bState.setFreeListList(nullptr);
    }
    _freeListsEnabled = false;
}


void
DataStoreBase::enableFreeList(uint32_t bufferId)
{
    BufferState &state = _states[bufferId];
    if (_freeListsEnabled &&
        state._state == BufferState::ACTIVE &&
        !state.getCompacting())
        state.setFreeListList(&_freeListLists[state._typeId]);
}


void
DataStoreBase::disableFreeList(uint32_t bufferId)
{
    _states[bufferId].setFreeListList(NULL);
}


void
DataStoreBase::disableElemHoldList(void)
{
    for (auto &state : _states) {
        if (state._state != BufferState::FREE)
            state.disableElemHoldList();
    }
}


DataStoreBase::MemStats
DataStoreBase::getMemStats(void) const
{
    MemStats stats;

    for (const BufferState & bState: _states) {
        auto typeHandler = bState._typeHandler;
        BufferState::State state = bState._state;
        if ((state == BufferState::FREE) || (typeHandler == nullptr)) {
            ++stats._freeBuffers;
        } else if (state == BufferState::ACTIVE) {
            size_t elementSize = typeHandler->elementSize();
            ++stats._activeBuffers;
            stats._allocElems += bState._allocElems;
            stats._usedElems += bState._usedElems;
            stats._deadElems += bState._deadElems;
            stats._holdElems += bState._holdElems;
            stats._allocBytes += bState._allocElems * elementSize;
            stats._usedBytes += bState._usedElems * elementSize;
            stats._deadBytes += bState._deadElems * elementSize;
            stats._holdBytes += bState._holdElems * elementSize;
        } else if (state == BufferState::HOLD) {
            size_t elementSize = typeHandler->elementSize();
            ++stats._holdBuffers;
            stats._allocElems += bState._allocElems;
            stats._usedElems += bState._usedElems;
            stats._deadElems += bState._deadElems;
            stats._holdElems += bState._holdElems;
            stats._allocBytes += bState._allocElems * elementSize;
            stats._usedBytes += bState._usedElems * elementSize;
            stats._deadBytes += bState._deadElems * elementSize;
            stats._holdBytes += bState._holdElems * elementSize;
        } else {
            abort();
        }
    }
    return stats;
}


void
DataStoreBase::onActive(uint32_t bufferId, uint32_t typeId,
                        size_t sizeNeeded,
                        size_t maxClusters)
{
    assert(typeId < _typeHandlers.size());
    assert(bufferId < _numBuffers);
    BufferState &state = _states[bufferId];
    state.onActive(bufferId, typeId,
                   _typeHandlers[typeId],
                   sizeNeeded,
                   maxClusters,
                   _buffers[bufferId]);
    enableFreeList(bufferId);
}

std::vector<uint32_t>
DataStoreBase::startCompact(uint32_t typeId)
{
    std::vector<uint32_t> toHold;

    for (uint32_t bufferId = 0; bufferId < _numBuffers; ++bufferId) {
        BufferState &state = getBufferState(bufferId);
        if (state._state == BufferState::ACTIVE &&
            state.getTypeId() == typeId &&
            !state.getCompacting()) {
            state.setCompacting();
            toHold.push_back(bufferId);
            disableFreeList(bufferId);
        }
    }
    switchActiveBuffer(typeId, 0u);
    return toHold;
}

void
DataStoreBase::finishCompact(const std::vector<uint32_t> &toHold)
{
    for (uint32_t bufferId : toHold) {
        holdBuffer(bufferId);
    }
}


void
DataStoreBase::fallbackResize(uint32_t bufferId, uint64_t sizeNeeded)
{
    BufferState &state = getBufferState(bufferId);
    BufferState::Alloc toHoldBuffer;
    size_t oldUsedElems = state._usedElems;
    size_t oldAllocElems = state._allocElems;
    size_t elementSize = state._typeHandler->elementSize();
    state.fallbackResize(bufferId,
                         sizeNeeded,
                         _maxClusters,
                         _buffers[bufferId],
                         toHoldBuffer);
    GenerationHeldBase::UP
        hold(new FallbackHold(oldAllocElems * elementSize,
                              std::move(toHoldBuffer),
                              oldUsedElems,
                              state._typeHandler,
                              state._typeId));
    _genHolder.hold(std::move(hold));
}


uint32_t
DataStoreBase::startCompactWorstBuffer(uint32_t typeId) {
    uint32_t activeBufferId = getActiveBufferId(typeId);
    const BufferTypeBase *typeHandler = _typeHandlers[typeId];
    assert(typeHandler->getActiveBuffers() >= 1u);
    if (typeHandler->getActiveBuffers() == 1u) {
        // Single active buffer for type, no need for scan
        _states[activeBufferId].setCompacting();
        _states[activeBufferId].disableElemHoldList();
        disableFreeList(activeBufferId);
        switchActiveBuffer(typeId, 0u);
        return activeBufferId;
    }
    // Multiple active buffers for type, must perform full scan
    return startCompactWorstBuffer(activeBufferId,
                                   [=](const BufferState &state) { return state.isActive(typeId); });
}

uint32_t
DataStoreBase::startCompactWorstBuffer()
{
   return startCompactWorstBuffer(0, [](const BufferState &state){ return state.isActive(); });
}

template <typename BufferStateActiveFilter>
uint32_t
DataStoreBase::startCompactWorstBuffer(uint32_t initWorstBufferId, BufferStateActiveFilter &&filterFunc)
{
    uint32_t worstBufferId = initWorstBufferId;
    size_t worstDeadElems = 0;
    for (uint32_t bufferId = 0; bufferId < _numBuffers; ++bufferId) {
        const auto &state = getBufferState(bufferId);
        if (filterFunc(state)) {
            size_t deadElems = state.getDeadElems() - state.getTypeHandler()->getReservedElements(bufferId);
            if (deadElems > worstDeadElems) {
                worstBufferId = bufferId;
                worstDeadElems = deadElems;
            }
        }
    }
    auto &worstBufferState = getBufferState(worstBufferId);
    uint32_t activeBufferId = getActiveBufferId(worstBufferState.getTypeId());
    if ((worstBufferId == activeBufferId) ||
        activeWriteBufferTooDead(getBufferState(activeBufferId)))
    {
        switchActiveBuffer(worstBufferState.getTypeId(), 0u);
    }
    worstBufferState.setCompacting();
    worstBufferState.disableElemHoldList();
    disableFreeList(worstBufferId);
    return worstBufferId;
}


} // namespace datastore
} // namespace search
