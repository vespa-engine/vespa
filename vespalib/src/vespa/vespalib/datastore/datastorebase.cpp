// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "datastorebase.h"
#include "compact_buffer_candidates.h"
#include "compacting_buffers.h"
#include "compaction_spec.h"
#include "compaction_strategy.h"
#include <vespa/vespalib/util/generation_hold_list.hpp>
#include <vespa/vespalib/util/stringfmt.h>
#include <algorithm>
#include <limits>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.datastore.datastorebase");

using vespalib::GenerationHeldBase;

namespace vespalib {
template class GenerationHoldList<datastore::DataStoreBase::EntryRefHoldElem, false, true>;
}

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
    size_t dead_entries = state.stats().dead_entries();
    size_t deadBytes = dead_entries * state.getTypeHandler()->entry_size();
    return ((deadBytes >= TOO_DEAD_SLACK) && (dead_entries * 2 >= state.size()));
}

}

DataStoreBase::FallbackHold::FallbackHold(size_t bytesSize, BufferState::Alloc &&buffer, size_t used_entries,
                                          BufferTypeBase *typeHandler, uint32_t typeId)
    : GenerationHeldBase(bytesSize),
      _buffer(std::move(buffer)),
      _used_entries(used_entries),
      _typeHandler(typeHandler),
      _typeId(typeId)
{
}

DataStoreBase::FallbackHold::~FallbackHold()
{
    _typeHandler->destroy_entries(_buffer.get(), _used_entries);
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

    ~BufferHold() override {
        _dsb.doneHoldBuffer(_bufferId);
    }
};

DataStoreBase::DataStoreBase(uint32_t numBuffers, uint32_t offset_bits, size_t max_entries)
    : _entry_ref_hold_list(),
      _buffers(numBuffers),
      _primary_buffer_ids(),
      _stash(),
      _typeHandlers(),
      _free_lists(),
      _compaction_count(0u),
      _genHolder(),
      _max_entries(max_entries),
      _bufferIdLimit(0u),
      _hold_buffer_count(0u),
      _offset_bits(offset_bits),
      _freeListsEnabled(false),
      _disable_entry_hold_list(false),
      _initializing(false)
{
}

DataStoreBase::~DataStoreBase()
{
    disableFreeLists();
}

void
DataStoreBase::switch_primary_buffer(uint32_t typeId, size_t entries_needed)
{
    size_t buffer_id = getFirstFreeBufferId();
    if (buffer_id >= getMaxNumBuffers()) {
        LOG_ABORT(vespalib::make_string("switch_primary_buffer(%u, %zu): did not find a free buffer",
                                        typeId, entries_needed).c_str());
    }
    on_active(buffer_id, typeId, entries_needed);
    _primary_buffer_ids[typeId] = buffer_id;
}

bool
DataStoreBase::consider_grow_active_buffer(uint32_t type_id, size_t entries_needed)
{
    auto type_handler = _typeHandlers[type_id];
    uint32_t buffer_id = primary_buffer_id(type_id);
    uint32_t active_buffers_count = type_handler->get_active_buffers_count();
    constexpr uint32_t min_active_buffers = 4u;
    if (active_buffers_count < min_active_buffers) {
        return false;
    }
    if (type_handler->get_num_entries_for_new_buffer() == 0u) {
        return false;
    }
    assert(!getBufferState(buffer_id).getCompacting());
    uint32_t min_buffer_id = buffer_id;
    size_t min_used = getBufferState(buffer_id).size();
    uint32_t checked_active_buffers = 1u;
    for (auto alt_buffer_id : type_handler->get_active_buffers()) {
        const BufferState & state = getBufferState(alt_buffer_id);
        if (alt_buffer_id != buffer_id && !state.getCompacting()) {
            ++checked_active_buffers;
            if (state.size() < min_used) {
                min_buffer_id = alt_buffer_id;
                min_used = state.size();
            }
        }
    }
    if (checked_active_buffers < min_active_buffers) {
        return false;
    }
    if (entries_needed + min_used > type_handler->get_max_entries()) {
        return false;
    }
    if (min_buffer_id != buffer_id) {
        // Resume another active buffer for same type as primary buffer
        _primary_buffer_ids[type_id] = min_buffer_id;
        getBufferState(min_buffer_id).resume_primary_buffer(min_buffer_id);
    }
    return true;
}

uint32_t
DataStoreBase::getFirstFreeBufferId() {
    uint32_t buffer_id = 0;
    for (auto & buffer : _buffers) {
        BufferState * state = buffer.get_state_relaxed();
        if (state == nullptr || state->isFree()) {
            return buffer_id;
        }
        buffer_id++;
    }
    // No free buffer, return out of bounds
    return buffer_id;
}

BufferState &
DataStoreBase::getBufferState(uint32_t buffer_id) noexcept {
    assert(buffer_id < get_bufferid_limit_relaxed());
    BufferState * state = _buffers[buffer_id].get_state_relaxed();
    assert(state != nullptr);
    return *state;
}

void
DataStoreBase::switch_or_grow_primary_buffer(uint32_t typeId, size_t entries_needed)
{
    auto typeHandler = _typeHandlers[typeId];
    size_t num_entries_for_new_buffer = typeHandler->get_scaled_num_entries_for_new_buffer();
    uint32_t bufferId = primary_buffer_id(typeId);
    if (entries_needed + getBufferState(bufferId).size() >= num_entries_for_new_buffer) {
        if (consider_grow_active_buffer(typeId, entries_needed)) {
            bufferId = primary_buffer_id(typeId);
            if (entries_needed > getBufferState(bufferId).remaining()) {
                fallback_resize(bufferId, entries_needed);
            }
        } else {
            switch_primary_buffer(typeId, entries_needed);
        }
    } else {
        fallback_resize(bufferId, entries_needed);
    }
}

void
DataStoreBase::init_primary_buffers()
{
    uint32_t numTypes = _primary_buffer_ids.size();
    for (uint32_t typeId = 0; typeId < numTypes; ++typeId) {
        size_t buffer_id = getFirstFreeBufferId();
        assert(buffer_id <= get_bufferid_limit_relaxed());
        on_active(buffer_id, typeId, 0u);
        _primary_buffer_ids[typeId] = buffer_id;
    }
}

uint32_t
DataStoreBase::addType(BufferTypeBase *typeHandler)
{
    uint32_t typeId = _primary_buffer_ids.size();
    assert(typeId == _typeHandlers.size());
    typeHandler->clamp_max_entries(_max_entries);
    _primary_buffer_ids.push_back(0);
    _typeHandlers.push_back(typeHandler);
    _free_lists.emplace_back();
    return typeId;
}

void
DataStoreBase::assign_generation(generation_t current_gen)
{
    _genHolder.assign_generation(current_gen);
    _entry_ref_hold_list.assign_generation(current_gen);
}

void
DataStoreBase::doneHoldBuffer(uint32_t bufferId)
{
    assert(_hold_buffer_count > 0);
    --_hold_buffer_count;
    getBufferState(bufferId).onFree(_buffers[bufferId].get_atomic_buffer());
}

void
DataStoreBase::reclaim_memory(generation_t oldest_used_gen)
{
    reclaim_entry_refs(oldest_used_gen);  // Trim entries before trimming buffers
    _genHolder.reclaim(oldest_used_gen);
}

void
DataStoreBase::reclaim_all_memory()
{
    _entry_ref_hold_list.assign_generation(0);
    reclaim_all_entry_refs();
    _genHolder.reclaim_all();
}

void
DataStoreBase::dropBuffers()
{
    uint32_t buffer_id_limit = get_bufferid_limit_relaxed();
    for (uint32_t bufferId = 0; bufferId < buffer_id_limit; ++bufferId) {
        BufferAndMeta & buffer = _buffers[bufferId];
        BufferState * state = buffer.get_state_relaxed();
        assert(state != nullptr);
        state->dropBuffer(bufferId, buffer.get_atomic_buffer());
    }
    _genHolder.reclaim_all();
}

vespalib::MemoryUsage
DataStoreBase::getDynamicMemoryUsage() const
{
    auto stats = getMemStats();
    vespalib::MemoryUsage usage;
    usage.setAllocatedBytes(stats._allocBytes);
    usage.setUsedBytes(stats._usedBytes);
    usage.setDeadBytes(stats._deadBytes);
    usage.setAllocatedBytesOnHold(stats._holdBytes);
    return usage;
}

vespalib::MemoryUsage
DataStoreBase::getMemoryUsage() const {
    auto usage = getDynamicMemoryUsage();
    size_t extra_allocated = 0;
    extra_allocated += _buffers.capacity() * sizeof(BufferAndMeta);
    extra_allocated += _primary_buffer_ids.capacity() * sizeof(uint32_t);
    extra_allocated += _typeHandlers.capacity() * sizeof(BufferTypeBase *);
    extra_allocated += _free_lists.capacity() * sizeof(FreeList);

    size_t extra_used = 0;
    extra_used += _buffers.size() * sizeof(BufferAndMeta);
    extra_used += _primary_buffer_ids.size() * sizeof(uint32_t);
    extra_used += _typeHandlers.size() * sizeof(BufferTypeBase *);
    extra_used += _free_lists.size() * sizeof(FreeList);
    usage.incAllocatedBytes(extra_allocated);
    usage.incUsedBytes(extra_used);
    usage.merge(_stash.get_memory_usage());
    return usage;
}

void
DataStoreBase::holdBuffer(uint32_t bufferId)
{
    getBufferState(bufferId).onHold(bufferId);
    size_t holdBytes = 0u;  // getMemStats() still accounts held buffers
    auto hold = std::make_unique<BufferHold>(holdBytes, *this, bufferId);
    _genHolder.insert(std::move(hold));
}

void
DataStoreBase::enableFreeLists()
{
    for_each_buffer([this](BufferState & state) {
        if (!state.isActive() || state.getCompacting()) return;
        state.enable_free_list(_free_lists[state.getTypeId()]);
    });
    _freeListsEnabled = true;
}

void
DataStoreBase::disableFreeLists()
{
    for_each_buffer([](BufferState & state) { state.disable_free_list(); });
    _freeListsEnabled = false;
}

void
DataStoreBase::disable_entry_hold_list()
{
    for_each_buffer([](BufferState & state) {
        if (!state.isFree()) state.disable_entry_hold_list();
    });
    _disable_entry_hold_list = true;
}

MemoryStats
DataStoreBase::getMemStats() const
{
    MemoryStats stats;

    uint32_t buffer_id_limit = get_bufferid_limit_acquire();
    stats._freeBuffers = (getMaxNumBuffers() - buffer_id_limit);
    for (uint32_t bufferId = 0; bufferId < buffer_id_limit; ++bufferId) {
        const BufferState * bState = _buffers[bufferId].get_state_acquire();
        assert(bState != nullptr);
        auto typeHandler = bState->getTypeHandler();
        auto state = bState->getState();
        if ((state == BufferState::State::FREE) || (typeHandler == nullptr)) {
            ++stats._freeBuffers;
        } else if (state == BufferState::State::ACTIVE) {
            size_t entry_size = typeHandler->entry_size();
            ++stats._activeBuffers;
            bState->stats().add_to_mem_stats(entry_size, stats);
        } else if (state == BufferState::State::HOLD) {
            size_t entry_size = typeHandler->entry_size();
            ++stats._holdBuffers;
            bState->stats().add_to_mem_stats(entry_size, stats);
        } else {
            LOG_ABORT("should not be reached");
        }
    }
    size_t genHolderHeldBytes = _genHolder.get_held_bytes();
    stats._holdBytes += genHolderHeldBytes;
    stats._allocBytes += genHolderHeldBytes;
    stats._usedBytes += genHolderHeldBytes;
    return stats;
}

vespalib::AddressSpace
DataStoreBase::getAddressSpaceUsage() const
{
    uint32_t buffer_id_limit = get_bufferid_limit_acquire();
    size_t used_entries = 0;
    size_t dead_entries = 0;
    size_t limit_entries = size_t(_max_entries) * (getMaxNumBuffers() - buffer_id_limit);
    for (uint32_t bufferId = 0; bufferId < buffer_id_limit; ++bufferId) {
        const BufferState * bState = _buffers[bufferId].get_state_acquire();
        assert(bState != nullptr);
        if (bState->isFree()) {
            limit_entries += _max_entries;
        } else if (bState->isActive()) {
            used_entries += bState->size();
            dead_entries += bState->stats().dead_entries();
            limit_entries += bState->capacity();
        } else if (bState->isOnHold()) {
            used_entries += bState->size();
            limit_entries += bState->capacity();
        } else {
            LOG_ABORT("should not be reached");
        }
    }
    return {used_entries, dead_entries, limit_entries};
}

void
DataStoreBase::on_active(uint32_t bufferId, uint32_t typeId, size_t entries_needed)
{
    assert(typeId < _typeHandlers.size());
    assert(bufferId <= _bufferIdLimit);

    BufferAndMeta & bufferMeta = _buffers[bufferId];
    BufferState *state = bufferMeta.get_state_relaxed();
    if (state == nullptr) {
        BufferState & newState = _stash.create<BufferState>();
        if (_disable_entry_hold_list) {
            newState.disable_entry_hold_list();
        }
        if ( ! _freeListsEnabled) {
            newState.disable_free_list();
        }
        state = & newState;
        bufferMeta.set_state(state);
        _bufferIdLimit.store(bufferId + 1, std::memory_order_release);
    }
    assert(state->isFree());
    state->on_active(bufferId, typeId, _typeHandlers[typeId], entries_needed, bufferMeta.get_atomic_buffer());
    bufferMeta.setTypeId(typeId);
    bufferMeta.setArraySize(state->getArraySize());
    if (_freeListsEnabled && state->isActive() && !state->getCompacting()) {
        state->enable_free_list(_free_lists[state->getTypeId()]);
    }
}

void
DataStoreBase::finishCompact(const std::vector<uint32_t> &toHold)
{
    for (uint32_t bufferId : toHold) {
        assert(getBufferState(bufferId).getCompacting());
        holdBuffer(bufferId);
    }
}

void
DataStoreBase::fallback_resize(uint32_t bufferId, size_t entries_needed)
{
    BufferState &state = getBufferState(bufferId);
    BufferState::Alloc toHoldBuffer;
    size_t old_used_entries = state.size();
    size_t old_alloc_entries = state.capacity();
    size_t entry_size = state.getTypeHandler()->entry_size();
    state.fallback_resize(bufferId, entries_needed, _buffers[bufferId].get_atomic_buffer(), toHoldBuffer);
    auto hold = std::make_unique<FallbackHold>(old_alloc_entries * entry_size,
                                               std::move(toHoldBuffer),
                                               old_used_entries,
                                               state.getTypeHandler(),
                                               state.getTypeId());
    if (!_initializing) {
        _genHolder.insert(std::move(hold));
    }
}

void
DataStoreBase::markCompacting(uint32_t bufferId)
{
    auto &state = getBufferState(bufferId);
    uint32_t typeId = state.getTypeId();
    uint32_t buffer_id = primary_buffer_id(typeId);
    if ((bufferId == buffer_id) || primary_buffer_too_dead(getBufferState(buffer_id))) {
        switch_primary_buffer(typeId, 0u);
    }
    assert(!state.getCompacting());
    state.setCompacting();
    state.disable_entry_hold_list();
    state.disable_free_list();
    inc_compaction_count();
}

std::unique_ptr<CompactingBuffers>
DataStoreBase::start_compact_worst_buffers(CompactionSpec compaction_spec, const CompactionStrategy& compaction_strategy)
{
    uint32_t buffer_id_limit = get_bufferid_limit_relaxed();
    // compact memory usage
    CompactBufferCandidates elem_buffers(buffer_id_limit, compaction_strategy.get_max_buffers(),
                                         compaction_strategy.get_active_buffers_ratio(),
                                         compaction_strategy.getMaxDeadBytesRatio() / 2,
                                         CompactionStrategy::DEAD_BYTES_SLACK);
    // compact address space
    CompactBufferCandidates array_buffers(buffer_id_limit, compaction_strategy.get_max_buffers(),
                                          compaction_strategy.get_active_buffers_ratio(),
                                          compaction_strategy.getMaxDeadAddressSpaceRatio() / 2,
                                          CompactionStrategy::DEAD_ADDRESS_SPACE_SLACK);
    uint32_t free_buffers = getMaxNumBuffers() - buffer_id_limit;
    for (uint32_t bufferId = 0; bufferId < buffer_id_limit; ++bufferId) {
        BufferState * state = _buffers[bufferId].get_state_relaxed();
        assert(state != nullptr);
        if (state->isFree()) {
            free_buffers++;
        } else if (state->isActive()) {
            auto typeHandler = state->getTypeHandler();
            uint32_t reserved_entries = typeHandler->get_reserved_entries(bufferId);
            size_t used_entries = state->size();
            size_t dead_entries = state->stats().dead_entries() - reserved_entries;
            size_t entry_size = typeHandler->entry_size();
            if (compaction_spec.compact_memory()) {
                elem_buffers.add(bufferId, used_entries * entry_size, dead_entries * entry_size);
            }
            if (compaction_spec.compact_address_space()) {
                array_buffers.add(bufferId, used_entries, dead_entries);
            }
        }
    }
    elem_buffers.set_free_buffers(free_buffers);
    array_buffers.set_free_buffers(free_buffers);
    std::vector<uint32_t> result;
    result.reserve(std::min(buffer_id_limit, 2 * compaction_strategy.get_max_buffers()));
    elem_buffers.select(result);
    array_buffers.select(result);
    std::sort(result.begin(), result.end());
    auto last = std::unique(result.begin(), result.end());
    result.erase(last, result.end());
    for (auto buffer_id : result) {
        markCompacting(buffer_id);
    }
    return std::make_unique<CompactingBuffers>(*this, buffer_id_limit, _offset_bits, std::move(result));
}

void
DataStoreBase::inc_hold_buffer_count()
{
    assert(_hold_buffer_count < std::numeric_limits<uint32_t>::max());
    ++_hold_buffer_count;
}

}
