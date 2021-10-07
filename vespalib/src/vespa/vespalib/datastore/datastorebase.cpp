// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "datastore.h"
#include <vespa/vespalib/util/array.hpp>
#include <vespa/vespalib/util/stringfmt.h>
#include <limits>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.datastore.datastorebase");

using vespalib::GenerationHeldBase;

namespace vespalib::datastore {

namespace {

/**
 * Minimum dead bytes in primary write buffer before switching to new
 * primary write buffer even if another active buffer has more dead
 * bytes due to considering the primary write buffer as too dead.
 */
constexpr size_t TOO_DEAD_SLACK = 0x4000u;

/**
 * Check if primary write buffer is too dead for further use, i.e. if it
 * is likely to be the worst buffer at next compaction.  If so, filling it
 * up completely will be wasted work, as data will have to be moved again
 * rather soon.
 */
bool
primary_buffer_too_dead(const BufferState &state)
{
    size_t deadElems = state.getDeadElems();
    size_t deadBytes = deadElems * state.getArraySize();
    return ((deadBytes >= TOO_DEAD_SLACK) && (deadElems * 2 >= state.size()));
}

}

DataStoreBase::FallbackHold::FallbackHold(size_t bytesSize,
                                          BufferState::Alloc &&buffer,
                                          size_t usedElems,
                                          BufferTypeBase *typeHandler,
                                          uint32_t typeId)
    : GenerationHeldBase(bytesSize),
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

class DataStoreBase::BufferHold : public GenerationHeldBase {
    DataStoreBase &_dsb;
    uint32_t _bufferId;

public:
    BufferHold(size_t bytesSize, DataStoreBase &dsb, uint32_t bufferId)
        : GenerationHeldBase(bytesSize),
          _dsb(dsb),
          _bufferId(bufferId)
    {
        _dsb.inc_hold_buffer_count();
    }

    ~BufferHold() override
    {
        _dsb.doneHoldBuffer(_bufferId);
    }
};

DataStoreBase::DataStoreBase(uint32_t numBuffers, size_t maxArrays)
    : _buffers(numBuffers),
      _primary_buffer_ids(),
      _states(numBuffers),
      _typeHandlers(),
      _freeListLists(),
      _freeListsEnabled(false),
      _initializing(false),
      _elemHold1List(),
      _elemHold2List(),
      _numBuffers(numBuffers),
      _hold_buffer_count(0u),
      _maxArrays(maxArrays),
      _compaction_count(0u),
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
DataStoreBase::switch_primary_buffer(uint32_t typeId, size_t elemsNeeded)
{
    size_t buffer_id = _primary_buffer_ids[typeId];
    for (size_t i = 0; i < getNumBuffers(); ++i) {
        // start using next buffer
        buffer_id = nextBufferId(buffer_id);
        if (_states[buffer_id].isFree()) {
            break;
        }
    }
    if (!_states[buffer_id].isFree()) {
        LOG_ABORT(vespalib::make_string("switch_primary_buffer(%u, %zu): did not find a free buffer",
                                        typeId, elemsNeeded).c_str());
    }
    onActive(buffer_id, typeId, elemsNeeded);
    _primary_buffer_ids[typeId] = buffer_id;
}

bool
DataStoreBase::consider_grow_active_buffer(uint32_t type_id, size_t elems_needed)
{
    auto type_handler = _typeHandlers[type_id];
    uint32_t buffer_id = _primary_buffer_ids[type_id];
    uint32_t active_buffers_count = type_handler->get_active_buffers_count();
    constexpr uint32_t min_active_buffers = 4u;
    if (active_buffers_count < min_active_buffers) {
        return false;
    }
    if (type_handler->get_num_arrays_for_new_buffer() == 0u) {
        return false;
    }
    assert(!_states[buffer_id].getCompacting());
    uint32_t min_buffer_id = buffer_id;
    size_t min_used = _states[buffer_id].size();
    uint32_t checked_active_buffers = 1u;
    for (auto &alt_buffer_id : type_handler->get_active_buffers()) {
        if (alt_buffer_id != buffer_id && !_states[alt_buffer_id].getCompacting()) {
            ++checked_active_buffers;
            if (_states[alt_buffer_id].size() < min_used) {
                min_buffer_id = alt_buffer_id;
                min_used = _states[alt_buffer_id].size();
            }
        }
    }
    if (checked_active_buffers < min_active_buffers) {
        return false;
    }
    auto array_size = type_handler->getArraySize();
    if (elems_needed + min_used > type_handler->getMaxArrays() * array_size) {
        return false;
    }
    if (min_buffer_id != buffer_id) {
        // Resume another active buffer for same type as primary buffer
        _primary_buffer_ids[type_id] = min_buffer_id;
        _states[min_buffer_id].resume_primary_buffer(min_buffer_id);
    }
    return true;
}

void
DataStoreBase::switch_or_grow_primary_buffer(uint32_t typeId, size_t elemsNeeded)
{
    auto typeHandler = _typeHandlers[typeId];
    uint32_t arraySize = typeHandler->getArraySize();
    size_t numArraysForNewBuffer = typeHandler->get_scaled_num_arrays_for_new_buffer();
    size_t numEntriesForNewBuffer = numArraysForNewBuffer * arraySize;
    uint32_t bufferId = _primary_buffer_ids[typeId];
    if (elemsNeeded + _states[bufferId].size() >= numEntriesForNewBuffer) {
        if (consider_grow_active_buffer(typeId, elemsNeeded)) {
            bufferId = _primary_buffer_ids[typeId];
            if (elemsNeeded > _states[bufferId].remaining()) {
                fallbackResize(bufferId, elemsNeeded);
            }
        } else {
            switch_primary_buffer(typeId, elemsNeeded);
        }
    } else {
        fallbackResize(bufferId, elemsNeeded);
    }
}

void
DataStoreBase::init_primary_buffers()
{
    uint32_t numTypes = _primary_buffer_ids.size();
    for (uint32_t typeId = 0; typeId < numTypes; ++typeId) {
        size_t buffer_id = 0;
        for (size_t i = 0; i < getNumBuffers(); ++i) {
            if (_states[buffer_id].isFree()) {
                 break;
            }
            // start using next buffer
            buffer_id = nextBufferId(buffer_id);
        }
        assert(_states[buffer_id].isFree());
        onActive(buffer_id, typeId, 0u);
        _primary_buffer_ids[typeId] = buffer_id;
    }
}

uint32_t
DataStoreBase::addType(BufferTypeBase *typeHandler)
{
    uint32_t typeId = _primary_buffer_ids.size();
    assert(typeId == _typeHandlers.size());
    typeHandler->clampMaxArrays(_maxArrays);
    _primary_buffer_ids.push_back(0);
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
    if (hasElemHold1()) {
        transferElemHoldList(generation);
    }
}

void
DataStoreBase::doneHoldBuffer(uint32_t bufferId)
{
    assert(_hold_buffer_count > 0);
    --_hold_buffer_count;
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
        _states[bufferId].dropBuffer(bufferId, _buffers[bufferId].getBuffer());
    }
    _genHolder.clearHoldLists();
}

vespalib::MemoryUsage
DataStoreBase::getMemoryUsage() const
{
    MemStats stats = getMemStats();
    vespalib::MemoryUsage usage;
    usage.setAllocatedBytes(stats._allocBytes);
    usage.setUsedBytes(stats._usedBytes);
    usage.setDeadBytes(stats._deadBytes);
    usage.setAllocatedBytesOnHold(stats._holdBytes);
    return usage;
}

void
DataStoreBase::holdBuffer(uint32_t bufferId)
{
    _states[bufferId].onHold(bufferId);
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
    _states[bufferId].setFreeListList(nullptr);
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

namespace {

void
add_buffer_state_to_mem_stats(const BufferState& state, size_t elementSize, DataStoreBase::MemStats& stats)
{
    size_t extra_used_bytes = state.getExtraUsedBytes();
    stats._allocElems += state.capacity();
    stats._usedElems += state.size();
    stats._deadElems += state.getDeadElems();
    stats._holdElems += state.getHoldElems();
    stats._allocBytes += (state.capacity() * elementSize) + extra_used_bytes;
    stats._usedBytes += (state.size() * elementSize) + extra_used_bytes;
    stats._deadBytes += state.getDeadElems() * elementSize;
    stats._holdBytes += (state.getHoldElems() * elementSize) + state.getExtraHoldBytes();
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
            add_buffer_state_to_mem_stats(bState, elementSize, stats);
        } else if (state == BufferState::HOLD) {
            size_t elementSize = typeHandler->elementSize();
            ++stats._holdBuffers;
            add_buffer_state_to_mem_stats(bState, elementSize, stats);
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

vespalib::AddressSpace
DataStoreBase::getAddressSpaceUsage() const
{
    size_t usedArrays = 0;
    size_t deadArrays = 0;
    size_t limitArrays = 0;
    for (const BufferState & bState: _states) {
        if (bState.isActive()) {
            uint32_t arraySize = bState.getArraySize();
            usedArrays += bState.size() / arraySize;
            deadArrays += bState.getDeadElems() / arraySize;
            limitArrays += bState.capacity() / arraySize;
        } else if (bState.isOnHold()) {
            uint32_t arraySize = bState.getArraySize();
            usedArrays += bState.size() / arraySize;
            limitArrays += bState.capacity() / arraySize;
        } else if (bState.isFree()) {
            limitArrays += _maxArrays;
        } else {
            LOG_ABORT("should not be reached");
        }
    }
    return vespalib::AddressSpace(usedArrays, deadArrays, limitArrays);
}

void
DataStoreBase::onActive(uint32_t bufferId, uint32_t typeId, size_t elemsNeeded)
{
    assert(typeId < _typeHandlers.size());
    assert(bufferId < _numBuffers);
    _buffers[bufferId].setTypeId(typeId);
    BufferState &state = _states[bufferId];
    state.onActive(bufferId, typeId,
                   _typeHandlers[typeId],
                   elemsNeeded,
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
    switch_primary_buffer(typeId, 0u);
    inc_compaction_count();
    return toHold;
}

void
DataStoreBase::finishCompact(const std::vector<uint32_t> &toHold)
{
    for (uint32_t bufferId : toHold) {
        assert(_states[bufferId].getCompacting());
        holdBuffer(bufferId);
    }
}

void
DataStoreBase::fallbackResize(uint32_t bufferId, size_t elemsNeeded)
{
    BufferState &state = getBufferState(bufferId);
    BufferState::Alloc toHoldBuffer;
    size_t oldUsedElems = state.size();
    size_t oldAllocElems = state.capacity();
    size_t elementSize = state.getTypeHandler()->elementSize();
    state.fallbackResize(bufferId, elemsNeeded,
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
DataStoreBase::startCompactWorstBuffer(uint32_t typeId)
{
    uint32_t buffer_id = get_primary_buffer_id(typeId);
    const BufferTypeBase *typeHandler = _typeHandlers[typeId];
    assert(typeHandler->get_active_buffers_count() >= 1u);
    if (typeHandler->get_active_buffers_count() == 1u) {
        // Single active buffer for type, no need for scan
        markCompacting(buffer_id);
        return buffer_id;
    }
    // Multiple active buffers for type, must perform full scan
    return startCompactWorstBuffer(buffer_id,
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
            assert(!state.getCompacting());
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
    uint32_t buffer_id = get_primary_buffer_id(typeId);
    if ((bufferId == buffer_id) || primary_buffer_too_dead(getBufferState(buffer_id))) {
        switch_primary_buffer(typeId, 0u);
    }
    assert(!state.getCompacting());
    state.setCompacting();
    state.disableElemHoldList();
    state.setFreeListList(nullptr);
    inc_compaction_count();
}

std::vector<uint32_t>
DataStoreBase::startCompactWorstBuffers(bool compactMemory, bool compactAddressSpace)
{
    constexpr uint32_t noBufferId = std::numeric_limits<uint32_t>::max();
    uint32_t worstMemoryBufferId = noBufferId;
    uint32_t worstAddressSpaceBufferId = noBufferId;
    size_t worstDeadElems = 0;
    size_t worstDeadArrays = 0;
    for (uint32_t bufferId = 0; bufferId < _numBuffers; ++bufferId) {
        const auto &state = getBufferState(bufferId);
        if (state.isActive()) {
            auto typeHandler = state.getTypeHandler();
            uint32_t arraySize = typeHandler->getArraySize();
            uint32_t reservedElements = typeHandler->getReservedElements(bufferId);
            size_t deadElems = state.getDeadElems() - reservedElements;
            if (compactMemory && deadElems > worstDeadElems) {
                worstMemoryBufferId = bufferId;
                worstDeadElems = deadElems;
            }
            if (compactAddressSpace) {
                size_t deadArrays = deadElems / arraySize;
                if (deadArrays > worstDeadArrays) {
                    worstAddressSpaceBufferId = bufferId;
                    worstDeadArrays = deadArrays;
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

void
DataStoreBase::inc_hold_buffer_count()
{
    assert(_hold_buffer_count < std::numeric_limits<uint32_t>::max());
    ++_hold_buffer_count;
}

}

