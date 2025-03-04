// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "data_store_explorer.h"
#include "datastorebase.h"
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <vespa/vespalib/util/state_explorer_utils.h>
#include <algorithm>

using vespalib::slime::ArrayInserter;
using vespalib::slime::Cursor;
using vespalib::slime::Inserter;
using vespalib::slime::ObjectInserter;

namespace vespalib::datastore {

namespace {

class BufferTypeStateStats {
    uint32_t _buffers;
    uint32_t _allocated_entries;
    uint32_t _used_entries;
    uint32_t _dead_entries;
    uint32_t _hold_entries;
    uint64_t _extra_used_bytes;
    uint64_t _extra_hold_bytes;
public:
    BufferTypeStateStats() noexcept;
    void aggregate(const BufferState& state) noexcept;
    void stats_to_slime(Inserter& inserter);
    uint32_t buffers() const noexcept { return _buffers; }
    uint32_t allocated_entries() const noexcept { return _allocated_entries; }
    uint32_t used_entries() const noexcept { return _used_entries; }
    uint32_t dead_entries() const noexcept { return _dead_entries; }
    uint32_t hold_entries() const noexcept { return _hold_entries; }
    uint64_t extra_used_bytes() const noexcept { return _extra_used_bytes; }
    uint64_t extra_hold_bytes() const noexcept { return _extra_hold_bytes; }
};

BufferTypeStateStats::BufferTypeStateStats() noexcept
    : _buffers(0),
      _allocated_entries(0),
      _used_entries(0),
      _dead_entries(0),
      _hold_entries(0),
      _extra_used_bytes(0),
      _extra_hold_bytes(0)
{
}

void
BufferTypeStateStats::aggregate(const BufferState& state) noexcept
{
    ++_buffers;
    _allocated_entries += state.capacity();
    _used_entries += state.size();
    _dead_entries += state.stats().dead_entries();
    _hold_entries += state.stats().hold_entries();
    _extra_used_bytes += state.stats().extra_used_bytes();
    _extra_hold_bytes += state.stats().extra_hold_bytes();
}


void
BufferTypeStateStats::stats_to_slime(Inserter& inserter)
{
    if (_buffers != 0) {
        auto& object = inserter.insertObject();
        object.setLong("count", _buffers);
        object.setLong("allocated_entries", _allocated_entries);
        object.setLong("used_entries", _used_entries);
        object.setLong("dead_entries", _dead_entries);
        object.setLong("hold_entries", _hold_entries);
        object.setLong("extra_used_bytes", _extra_used_bytes);
        object.setLong("extra_hold_bytes", _extra_hold_bytes);
    }
}

class BufferTypeStats {
    uint32_t _type_id;
    uint32_t _entry_size;
    uint32_t _array_size;
    uint32_t _max_entries;
    BufferTypeStateStats _active;
    BufferTypeStateStats _hold;

public:
    BufferTypeStats() noexcept;
    bool is_initialized() const noexcept { return _active.buffers() != 0 || _hold.buffers() != 0; }
    void aggregate(const BufferState& state) noexcept;
    void stats_to_slime(Inserter& inserter);
    uint32_t type_id() const noexcept { return _type_id; }
    uint64_t entry_size() const noexcept { return _entry_size; }
    const BufferTypeStateStats& active() const noexcept { return _active; }
    const BufferTypeStateStats& hold() const noexcept { return _hold; }
    uint32_t buffers() const noexcept { return active().buffers() + hold().buffers(); }
    uint32_t allocated_entries() const noexcept { return active().allocated_entries() + hold().allocated_entries(); }
    uint32_t used_entries() const noexcept { return active().used_entries() + hold().used_entries(); }
    uint32_t dead_entries() const noexcept { return active().dead_entries() + hold().dead_entries(); }
    uint32_t hold_entries() const noexcept { return active().hold_entries() + hold().hold_entries(); }
    uint64_t extra_used_bytes() const noexcept { return active().extra_used_bytes() + hold().extra_used_bytes(); }
    uint64_t extra_hold_bytes() const noexcept { return active().extra_hold_bytes() + hold().extra_hold_bytes(); }
    uint64_t used_bytes() const noexcept { return used_entries() * entry_size() + extra_used_bytes(); }
    uint64_t allocated_bytes() const noexcept { return allocated_entries() * entry_size() + extra_used_bytes(); }
    uint64_t hold_bytes() const noexcept { return hold_entries() * entry_size() + extra_hold_bytes(); }
    uint64_t dead_bytes() const noexcept { return dead_entries() * entry_size(); }
};

BufferTypeStats::BufferTypeStats() noexcept
    : _type_id(0),
      _entry_size(0),
      _array_size(0),
      _max_entries(0),
      _active(),
      _hold()
{
}

void
BufferTypeStats::aggregate(const BufferState& state) noexcept
{
    if (!is_initialized()) {
        _type_id = state.getTypeId();
        auto type_handler = state.getTypeHandler();
        _entry_size = type_handler->entry_size();
        _array_size = type_handler->getArraySize();
        _max_entries = type_handler->get_max_entries();
    }
    switch (state.getState()) {
        case BufferState::State::ACTIVE:
            _active.aggregate(state);
        break;
        case BufferState::State::HOLD:
            _hold.aggregate(state);
        break;
        case BufferState::State::FREE:
            ;
    }
}

void
BufferTypeStats::stats_to_slime(Inserter& inserter)
{
    auto& object = inserter.insertObject();
    object.setLong("type_id", _type_id);
    object.setLong("entry_size", _entry_size);
    object.setLong("array_size", _array_size);
    object.setLong("max_entries", _max_entries);
    object.setLong("allocated_bytes", allocated_bytes());
    object.setLong("used_bytes", used_bytes());
    object.setLong("dead_bytes",dead_bytes());
    object.setLong("hold_bytes", hold_bytes());
    ObjectInserter active_buffers(object, "active_buffers");
    _active.stats_to_slime(active_buffers);
    ObjectInserter hold_buffers(object, "hold_buffers");
    _hold.stats_to_slime(hold_buffers);
}

struct GreaterResourceUsage {
    static bool operator()(const BufferTypeStats& lhs, const BufferTypeStats &rhs) noexcept {
        if (lhs.buffers() != rhs.buffers()) {
            return lhs.buffers() > rhs.buffers();
        }
        if (lhs.active().buffers() != rhs.active().buffers()) {
            return lhs.active().buffers() > rhs.active().buffers();
        }
        if (lhs.used_bytes() != rhs.used_bytes()) {
            return lhs.used_bytes() > rhs.used_bytes();
        }
        if (lhs.used_entries() != rhs.used_entries()) {
            return lhs.used_entries() > rhs.used_entries();
        }
        if (lhs.type_id() != rhs.type_id()) {
            return lhs.type_id() < rhs.type_id();
        }
        return false;
    }
};

class Stats {
    uint32_t _type_id_limit;
    uint32_t _bufferid_limit;
    uint32_t _max_num_buffers;
    uint32_t _max_entries;
    uint32_t _active_buffers;
    uint32_t _free_buffers;
    uint32_t _hold_buffers;
    std::vector<BufferTypeStats> _buffer_type_stats;
public:
    Stats();
    ~Stats();
    void buffer_stats_scan(const DataStoreBase& store);
    void buffer_type_scan(const DataStoreBase& store);
    void  buffer_stats_to_slime(Cursor& object);
    uint32_t buffer_type_stats_to_slime(Cursor& array);
    uint32_t type_id_limit() const noexcept { return _type_id_limit; }
    uint32_t bufferid_limit() const noexcept { return _bufferid_limit; }
    uint32_t max_num_buffers() const noexcept { return _max_num_buffers; }
    uint32_t max_entries() const noexcept { return _max_entries; }
};

Stats::Stats()
    : _type_id_limit(0),
      _bufferid_limit(0),
      _max_num_buffers(0),
      _max_entries(0),
      _active_buffers(0),
      _free_buffers(0),
      _hold_buffers(0),
      _buffer_type_stats()
{
}

Stats::~Stats() = default;

void
Stats::buffer_stats_scan(const DataStoreBase& store)
{
    _bufferid_limit = store.get_bufferid_limit_acquire();
    _max_num_buffers = store.getMaxNumBuffers();
    _max_entries = store.get_max_entries();
    _type_id_limit = 0;
    _active_buffers = 0;
    _free_buffers = _max_num_buffers - _bufferid_limit;
    _hold_buffers = 0;
    for (uint32_t id = 0; id < _bufferid_limit; ++id) {
        auto& buffer_meta = store.getBufferMeta(id);
        auto& state = *buffer_meta.get_state_acquire();
        switch (state.getState()) {
            case BufferState::State::ACTIVE:
                ++_active_buffers;
                _type_id_limit = std::max(_type_id_limit, buffer_meta.getTypeId() + 1);
            break;
            case BufferState::State::HOLD:
                ++_hold_buffers;
                _type_id_limit = std::max(_type_id_limit, buffer_meta.getTypeId() + 1);
            break;
            case BufferState::State::FREE:
                ++_free_buffers;
            break;
        }
    }
}

void
Stats::buffer_type_scan(const DataStoreBase& store)
{
    _buffer_type_stats.clear();
    _buffer_type_stats.resize(_type_id_limit);
    for (uint32_t id = 0; id < _bufferid_limit; ++id) {
        auto& buffer_meta = store.getBufferMeta(id);
        auto& state = *buffer_meta.get_state_acquire();
        if (state.getState() != BufferState::State::FREE) {
            auto type_id = buffer_meta.getTypeId();
            _buffer_type_stats[type_id].aggregate(state);
        }
    }
}

void
Stats::buffer_stats_to_slime(Cursor& object)
{
    object.setLong("active", _active_buffers);
    object.setLong("hold", _hold_buffers);
    object.setLong("free", _free_buffers);
}

uint32_t
Stats::buffer_type_stats_to_slime(Cursor& array)
{
    std::sort(_buffer_type_stats.begin(), _buffer_type_stats.end(), GreaterResourceUsage());
    uint32_t skipped = 0;
    ArrayInserter ai(array);
    for (auto& stats : _buffer_type_stats) {
        if (stats.allocated_entries() > 0) {
            stats.stats_to_slime(ai);
        } else {
            ++skipped;
        }
    }
    return skipped;
}

}

DataStoreExplorer::DataStoreExplorer(const DataStoreBase &store)
    : StateExplorer(),
      _store(store)
{
}

DataStoreExplorer::~DataStoreExplorer() = default;

void
DataStoreExplorer::get_state(const Inserter& inserter, bool full) const
{
    auto& object = inserter.insertObject();
    StateExplorerUtils::memory_usage_to_slime(_store.getMemoryUsage(), object.setObject("memory_usage"));
    StateExplorerUtils::memory_usage_to_slime(_store.getDynamicMemoryUsage(), object.setObject("dynamic_memory_usage"));
    StateExplorerUtils::address_space_to_slime(_store.getAddressSpaceUsage(), object.setObject("address_space"));
    Stats stats;
    stats.buffer_stats_scan(_store);
    object.setLong("bufferid_limit", stats.bufferid_limit());
    object.setLong("max_num_buffers", stats.max_num_buffers());
    object.setLong("typeid_limit", stats.type_id_limit());
    object.setLong("max_entries", stats.max_entries());
    stats.buffer_stats_to_slime(object.setObject("buffer_stats"));
    if (full) {
        stats.buffer_type_scan(_store);
        auto skipped = stats.buffer_type_stats_to_slime(object.setArray("buffer_types"));
        object.setLong("skipped_buffer_types", skipped);
    }
}

}
