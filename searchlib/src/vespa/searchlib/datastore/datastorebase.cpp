// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "datastore.h"
#include <vespa/vespalib/util/array.hpp>
#include <limits>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.datastore.datastorebase");

using vespalib::GenerationHeldBase;

namespace search::datastore {

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


DataStoreBase::FallbackHold::~FallbackHold()
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
    ~BufferHold()
    {
        _dsb.doneHoldBuffer(_bufferId);
    }
};


DataStoreBase::DataStoreBase(uint32_t numBuffers, size_t maxClusters)
    : _buffers(numBuffers),
      _activeBufferIds(),
      _states(numBuffers),
      _typeHandlers(),
      _freeListLists(),
      _freeListsEnabled(false),
      _initializing(false),
      _elemHold1List(),
      _elemHold2List(),
      _numBuffers(numBuffers),
      _maxClusters(maxClusters),
      _genHolder()
{
}


DataStoreBase::~DataStoreBase()
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
    } while (!_states[activeBufferId].isFree());
    onActive(activeBufferId, typeId, sizeNeeded);
    _activeBufferIds[typeId] = activeBufferId;
}


void
DataStoreBase::switchOrGrowActiveBuffer(uint32_t typeId, size_t sizeNeeded)
{
    auto typeHandler = _typeHandlers[typeId];
    uint32_t clusterSize = typeHandler->getClusterSize();
    size_t numClustersForNewBuffer = typeHandler->getNumClustersForNewBuffer();
    size_t numEntriesForNewBuffer = numClustersForNewBuffer * clusterSize;
    uint32_t bufferId = _activeBufferIds[typeId];
    if (sizeNeeded + _states[bufferId].size() >= numEntriesForNewBuffer) {
        // Don't try to resize existing buffer, new buffer will be large enough
        switchActiveBuffer(typeId, sizeNeeded);
    } else {
        fallbackResize(bufferId, sizeNeeded);
    }
}


void
DataStoreBase::initActiveBuffers()
{
    uint32_t numTypes = _activeBufferIds.size();
    for (uint32_t typeId = 0; typeId < numTypes; ++typeId) {
        size_t activeBufferId = 0;
        while (!_states[activeBufferId].isFree()) {
            // start using next buffer
            activeBufferId = nextBufferId(activeBufferId);
        }
        onActive(activeBufferId, typeId, 0u);
        _activeBufferIds[typeId] = activeBufferId;
    }
}


uint32_t
DataStoreBase::addType(BufferTypeBase *typeHandler)
{
    uint32_t typeId = _activeBufferIds.size();
    assert(typeId == _typeHandlers.size());
    typeHandler->clampMaxClusters(_maxClusters);
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
    _states[bufferId].onFree(_buffers[bufferId].getBuffer());
}


void
DataStoreBase::trimHoldLists(generation_t usedGen)
{
    trimElemHoldList(usedGen);  // Trim entries before trimming buffers

    _genHolder.trimHoldLists(usedGen);
}


void
DataStoreBase::clearHoldLists()
{
    transferElemHoldList(0);
    clearElemHoldList();
    _genHolder.clearHoldLists();
}


void
DataStoreBase::dropBuffers()
{
    uint32_t numBuffers = _buffers.size();
    for (uint32_t bufferId = 0; bufferId < numBuffers; ++bufferId) {
        _states[bufferId].dropBuffer(_buffers[bufferId].getBuffer());
    }
    _genHolder.clearHoldLists();
}


MemoryUsage
DataStoreBase::getMemoryUsage() const
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
    size_t holdBytes = 0u;  // getMemStats() still accounts held buffers
    GenerationHeldBase::UP hold(new BufferHold(holdBytes, *this, bufferId));
    _genHolder.hold(std::move(hold));
}


void
DataStoreBase::enableFreeLists()
{
    for (BufferState & bState : _states) {
        if (!bState.isActive() || bState.getCompacting()) {
            continue;
        }
        bState.setFreeListList(&_freeListLists[bState.getTypeId()]);
    }
    _freeListsEnabled = true;
}


void
DataStoreBase::disableFreeLists()
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
        state.isActive() &&
        !state.getCompacting()) {
        state.setFreeListList(&_freeListLists[state.getTypeId()]);
    }
}


void
DataStoreBase::disableFreeList(uint32_t bufferId)
{
    _states[bufferId].setFreeListList(NULL);
}


void
DataStoreBase::disableElemHoldList()
{
    for (auto &state : _states) {
        if (!state.isFree()) {
            state.disableElemHoldList();
        }
    }
}


DataStoreBase::MemStats
DataStoreBase::getMemStats() const
{
    MemStats stats;

    for (const BufferState & bState: _states) {
        auto typeHandler = bState.getTypeHandler();
        BufferState::State state = bState.getState();
        if ((state == BufferState::FREE) || (typeHandler == nullptr)) {
            ++stats._freeBuffers;
        } else if (state == BufferState::ACTIVE) {
            size_t elementSize = typeHandler->elementSize();
            ++stats._activeBuffers;
            stats._allocElems += bState.capacity();
            stats._usedElems += bState.size();
            stats._deadElems += bState.getDeadElems();
            stats._holdElems += bState.getHoldElems();
            stats._allocBytes += bState.capacity() * elementSize;
            stats._usedBytes += (bState.size() * elementSize) + bState.getExtraUsedBytes();
            stats._deadBytes += bState.getDeadElems() * elementSize;
            stats._holdBytes += (bState.getHoldElems() * elementSize) + bState.getExtraHoldBytes();
        } else if (state == BufferState::HOLD) {
            size_t elementSize = typeHandler->elementSize();
            ++stats._holdBuffers;
            stats._allocElems += bState.capacity();
            stats._usedElems += bState.size();
            stats._deadElems += bState.getDeadElems();
            stats._holdElems += bState.getHoldElems();
            stats._allocBytes += bState.capacity() * elementSize;
            stats._usedBytes += (bState.size() * elementSize) + bState.getExtraUsedBytes();
            stats._deadBytes += bState.getDeadElems() * elementSize;
            stats._holdBytes += (bState.getHoldElems() * elementSize) + bState.getExtraHoldBytes();
        } else {
            LOG_ABORT("should not be reached");
        }
    }
    size_t genHolderHeldBytes = _genHolder.getHeldBytes();
    stats._holdBytes += genHolderHeldBytes;
    stats._allocBytes += genHolderHeldBytes;
    stats._usedBytes += genHolderHeldBytes;
    return stats;
}

AddressSpace
DataStoreBase::getAddressSpaceUsage() const
{
    size_t usedClusters = 0;
    size_t deadClusters = 0;
    size_t limitClusters = 0;
    for (const BufferState & bState: _states) {
        if (bState.isActive()) {
            uint32_t clusterSize = bState.getClusterSize();
            usedClusters += bState.size() / clusterSize;
            deadClusters += bState.getDeadElems() / clusterSize;
            limitClusters += bState.capacity() / clusterSize;
        } else if (bState.isOnHold()) {
            uint32_t clusterSize = bState.getClusterSize();
            usedClusters += bState.size() / clusterSize;
            limitClusters += bState.capacity() / clusterSize;
        } else if (bState.isFree()) {
            limitClusters += _maxClusters;
        } else {
            LOG_ABORT("should not be reached");
        }
    }
    return AddressSpace(usedClusters, deadClusters, limitClusters);
}

void
DataStoreBase::onActive(uint32_t bufferId, uint32_t typeId, size_t sizeNeeded)
{
    assert(typeId < _typeHandlers.size());
    assert(bufferId < _numBuffers);
    _buffers[bufferId].setTypeId(typeId);
    BufferState &state = _states[bufferId];
    state.onActive(bufferId, typeId,
                   _typeHandlers[typeId],
                   sizeNeeded,
                   _buffers[bufferId].getBuffer());
    enableFreeList(bufferId);
}

std::vector<uint32_t>
DataStoreBase::startCompact(uint32_t typeId)
{
    std::vector<uint32_t> toHold;

    for (uint32_t bufferId = 0; bufferId < _numBuffers; ++bufferId) {
        BufferState &state = getBufferState(bufferId);
        if (state.isActive() &&
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
    size_t oldUsedElems = state.size();
    size_t oldAllocElems = state.capacity();
    size_t elementSize = state.getTypeHandler()->elementSize();
    state.fallbackResize(bufferId, sizeNeeded,
                         _buffers[bufferId].getBuffer(),
                         toHoldBuffer);
    GenerationHeldBase::UP
        hold(new FallbackHold(oldAllocElems * elementSize,
                              std::move(toHoldBuffer),
                              oldUsedElems,
                              state.getTypeHandler(),
                              state.getTypeId()));
    if (!_initializing) {
        _genHolder.hold(std::move(hold));
    }
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
    markCompacting(worstBufferId);
    return worstBufferId;
}

void
DataStoreBase::markCompacting(uint32_t bufferId)
{
    auto &state = getBufferState(bufferId);
    uint32_t typeId = state.getTypeId();
    uint32_t activeBufferId = getActiveBufferId(typeId);
    if ((bufferId == activeBufferId) || activeWriteBufferTooDead(getBufferState(activeBufferId))) {
        switchActiveBuffer(typeId, 0u);
    }
    state.setCompacting();
    state.disableElemHoldList();
    state.setFreeListList(nullptr);
}

std::vector<uint32_t>
DataStoreBase::startCompactWorstBuffers(bool compactMemory, bool compactAddressSpace)
{
    constexpr uint32_t noBufferId = std::numeric_limits<uint32_t>::max();
    uint32_t worstMemoryBufferId = noBufferId;
    uint32_t worstAddressSpaceBufferId = noBufferId;
    size_t worstDeadElems = 0;
    size_t worstDeadClusters = 0;
    for (uint32_t bufferId = 0; bufferId < _numBuffers; ++bufferId) {
        const auto &state = getBufferState(bufferId);
        if (state.isActive()) {
            auto typeHandler = state.getTypeHandler();
            uint32_t clusterSize = typeHandler->getClusterSize();
            uint32_t reservedElements = typeHandler->getReservedElements(bufferId);
            size_t deadElems = state.getDeadElems() - reservedElements;
            if (compactMemory && deadElems > worstDeadElems) {
                worstMemoryBufferId = bufferId;
                worstDeadElems = deadElems;
            }
            if (compactAddressSpace) {
                size_t deadClusters = deadElems / clusterSize;
                if (deadClusters > worstDeadClusters) {
                    worstAddressSpaceBufferId = bufferId;
                    worstDeadClusters = deadClusters;
                }
            }
        }
    }
    std::vector<uint32_t> result;
    if (worstMemoryBufferId != noBufferId) {
        markCompacting(worstMemoryBufferId);
        result.emplace_back(worstMemoryBufferId);
    }
    if (worstAddressSpaceBufferId != noBufferId &&
        worstAddressSpaceBufferId != worstMemoryBufferId) {
        markCompacting(worstAddressSpaceBufferId);
        result.emplace_back(worstAddressSpaceBufferId);
    }
    return result;
}

}

