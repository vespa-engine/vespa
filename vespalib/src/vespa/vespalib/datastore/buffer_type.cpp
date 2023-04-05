// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "buffer_type.hpp"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <algorithm>
#include <cassert>
#include <cmath>

namespace vespalib::datastore {

namespace {

constexpr float DEFAULT_ALLOC_GROW_FACTOR = 0.2;

}

void
BufferTypeBase::CleanContext::extraBytesCleaned(size_t value)
{
    size_t extra_used_bytes = _extraUsedBytes.load(std::memory_order_relaxed);
    size_t extra_hold_bytes = _extraHoldBytes.load(std::memory_order_relaxed);
    assert(extra_used_bytes >= value);
    assert(extra_hold_bytes >= value);
    _extraUsedBytes.store(extra_used_bytes - value, std::memory_order_relaxed);
    _extraHoldBytes.store(extra_hold_bytes - value, std::memory_order_relaxed);
}

BufferTypeBase::BufferTypeBase(uint32_t arraySize,
                               uint32_t min_entries,
                               uint32_t max_entries,
                               uint32_t num_entries_for_new_buffer,
                               float allocGrowFactor) noexcept
    : _arraySize(arraySize),
      _min_entries(std::min(min_entries, max_entries)),
      _max_entries(max_entries),
      _num_entries_for_new_buffer(std::min(num_entries_for_new_buffer, max_entries)),
      _allocGrowFactor(allocGrowFactor),
      _holdBuffers(0),
      _hold_used_entries(0),
      _aggr_counts(),
      _active_buffers()
{
}

BufferTypeBase::BufferTypeBase(uint32_t arraySize,
                               uint32_t min_entries,
                               uint32_t max_entries) noexcept
    : BufferTypeBase(arraySize, min_entries, max_entries, 0u, DEFAULT_ALLOC_GROW_FACTOR)
{
}

BufferTypeBase::~BufferTypeBase()
{
    assert(_holdBuffers == 0);
    assert(_hold_used_entries == 0);
    assert(_aggr_counts.empty());
    assert(_active_buffers.empty());
}

EntryCount
BufferTypeBase::get_reserved_entries(uint32_t bufferId) const
{
    return bufferId == 0 ? 1u : 0u;
}

void
BufferTypeBase::on_active(uint32_t bufferId, std::atomic<EntryCount>* used_entries, std::atomic<EntryCount>* dead_entries, void* buffer)
{
    _aggr_counts.add_buffer(used_entries, dead_entries);
    assert(std::find(_active_buffers.begin(), _active_buffers.end(), bufferId) == _active_buffers.end());
    _active_buffers.emplace_back(bufferId);
    auto reserved_entries = get_reserved_entries(bufferId);
    if (reserved_entries != 0u) {
        initialize_reserved_entries(buffer, reserved_entries);
        *used_entries = reserved_entries;
        *dead_entries = reserved_entries;
    }
}

void
BufferTypeBase::on_hold(uint32_t buffer_id, const std::atomic<EntryCount>* used_entries, const std::atomic<EntryCount>* dead_entries)
{
    ++_holdBuffers;
    auto itr = std::find(_active_buffers.begin(), _active_buffers.end(), buffer_id);
    assert(itr != _active_buffers.end());
    _active_buffers.erase(itr);
    _aggr_counts.remove_buffer(used_entries, dead_entries);
    _hold_used_entries += *used_entries;
}

void
BufferTypeBase::on_free(EntryCount used_entries)
{
    --_holdBuffers;
    assert(_hold_used_entries >= used_entries);
    _hold_used_entries -= used_entries;
}

void
BufferTypeBase::resume_primary_buffer(uint32_t buffer_id, std::atomic<EntryCount>* used_entries, std::atomic<EntryCount>* dead_entries)
{
    auto itr = std::find(_active_buffers.begin(), _active_buffers.end(), buffer_id);
    assert(itr != _active_buffers.end());
    _active_buffers.erase(itr);
    _active_buffers.emplace_back(buffer_id);
    _aggr_counts.remove_buffer(used_entries, dead_entries);
    _aggr_counts.add_buffer(used_entries, dead_entries);
}

const alloc::MemoryAllocator*
BufferTypeBase::get_memory_allocator() const
{
    return nullptr;
}

void
BufferTypeBase::clamp_max_entries(uint32_t max_entries)
{
    _max_entries = std::min(_max_entries, max_entries);
    _min_entries = std::min(_min_entries, _max_entries);
    _num_entries_for_new_buffer = std::min(_num_entries_for_new_buffer, _max_entries);
}

size_t
BufferTypeBase::calc_entries_to_alloc(uint32_t bufferId, EntryCount free_entries_needed, bool resizing) const
{
    size_t reserved_entries = get_reserved_entries(bufferId);
    BufferCounts last_bc;
    BufferCounts bc;
    if (resizing) {
        if (!_aggr_counts.empty()) {
            last_bc = _aggr_counts.last_buffer();
        }
    }
    bc = _aggr_counts.all_buffers();
    assert(bc.used_entries >= bc.dead_entries);
    size_t needed_entries = static_cast<size_t>(free_entries_needed) + (resizing ? last_bc.used_entries : reserved_entries);
    size_t live_entries = (bc.used_entries - bc.dead_entries);
    size_t grow_entries = (live_entries * _allocGrowFactor);
    size_t used_entries = last_bc.used_entries;
    size_t wanted_entries = std::max((resizing ? used_entries : 0u) + grow_entries,
                                     static_cast<size_t>(_min_entries));

    size_t result = wanted_entries;
    if (result < needed_entries) {
        result = needed_entries;
    }
    if (result > _max_entries) {
        result = _max_entries;
    }
    if (result < needed_entries) {
        vespalib::asciistream s;
        s << "BufferTypeBase::calcArraysToAlloc(" <<
            "bufferId=" << bufferId <<
            ",free_entries_needed=" << free_entries_needed <<
            ",resizing=" << (resizing ? "true" : "false") << ")" <<
            " wanted_entries=" << wanted_entries <<
            ", _arraySize=" << _arraySize <<
            ", _max_entries=" << _max_entries <<
            ", reserved_entries=" << reserved_entries <<
            ", live_entries=" << live_entries <<
            ", grow_entries=" << grow_entries <<
            ", used_entries=" << used_entries <<
            ", typeid(*this).name=\"" << typeid(*this).name() << "\"" <<
            ", new_entries=" << result <<
            " < needed_entries=" << needed_entries;
        throw vespalib::OverflowException(s.c_str());
    }
    return result;
}

uint32_t
BufferTypeBase::get_scaled_num_entries_for_new_buffer() const
{
    uint32_t active_buffers_count = get_active_buffers_count();
    if (active_buffers_count <= 1u || _num_entries_for_new_buffer == 0u) {
        return _num_entries_for_new_buffer;
    }
    double scale_factor = std::pow(1.0 + _allocGrowFactor, active_buffers_count - 1);
    double scaled_result = _num_entries_for_new_buffer * scale_factor;
    if (scaled_result >= _max_entries) {
        return _max_entries;
    }
    return scaled_result;
}

BufferTypeBase::AggregatedBufferCounts::AggregatedBufferCounts()
    : _counts()
{
}

void
BufferTypeBase::AggregatedBufferCounts::add_buffer(const std::atomic<EntryCount>* used_entries, const std::atomic<EntryCount>* dead_entries)
{
    for (const auto& elem : _counts) {
        assert(elem.used_ptr != used_entries);
        assert(elem.dead_ptr != dead_entries);
    }
    _counts.emplace_back(used_entries, dead_entries);
}

void
BufferTypeBase::AggregatedBufferCounts::remove_buffer(const std::atomic<EntryCount>* used_entries, const std::atomic<EntryCount>* dead_entries)
{
    auto itr = std::find_if(_counts.begin(), _counts.end(),
                            [=](const auto& elem){ return elem.used_ptr == used_entries; });
    assert(itr != _counts.end());
    assert(itr->dead_ptr == dead_entries);
    _counts.erase(itr);
}

BufferTypeBase::BufferCounts
BufferTypeBase::AggregatedBufferCounts::last_buffer() const
{
    BufferCounts result;
    assert(!_counts.empty());
    const auto& last = _counts.back();
    result.used_entries += last.used_ptr->load(std::memory_order_relaxed);
    result.dead_entries += last.dead_ptr->load(std::memory_order_relaxed);
    return result;
}

BufferTypeBase::BufferCounts
BufferTypeBase::AggregatedBufferCounts::all_buffers() const
{
    BufferCounts result;
    for (const auto& elem : _counts) {
        result.used_entries += elem.used_ptr->load(std::memory_order_relaxed);
        result.dead_entries += elem.dead_ptr->load(std::memory_order_relaxed);
    }
    return result;
}

template class BufferType<char>;
template class BufferType<uint8_t>;
template class BufferType<uint32_t>;
template class BufferType<uint64_t>;
template class BufferType<int32_t>;
template class BufferType<std::string>;
template class BufferType<AtomicEntryRef>;

}

