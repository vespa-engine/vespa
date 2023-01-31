// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "shared_string_repo.h"
#include <xxhash.h>
#include <charconv>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.shared_string_repo");

namespace vespalib {

namespace {

bool resolve_should_reclaim_flag() {
    bool no_reclaim = (getenv("VESPA_SHARED_STRING_REPO_NO_RECLAIM") != nullptr);
    return !no_reclaim;
}

}

const bool SharedStringRepo::should_reclaim = resolve_should_reclaim_flag();

SharedStringRepo::Stats::Stats()
    : active_entries(0),
      total_entries(0),
      max_part_usage(0),
      memory_usage()
{
}

void
SharedStringRepo::Stats::merge(const Stats &s)
{
    active_entries += s.active_entries;
    total_entries += s.total_entries;
    max_part_usage = std::max(max_part_usage, s.max_part_usage);
    memory_usage.merge(s.memory_usage);
}

size_t
SharedStringRepo::Stats::part_limit()
{
    return PART_LIMIT;
}

double
SharedStringRepo::Stats::id_space_usage() const
{
    return (double(max_part_usage) / double(PART_LIMIT));
}

SharedStringRepo::Partition::~Partition() = default;

SharedStringRepo::Partition::Partition()
    : _lock(), _entries(), _free(Entry::npos), _hash(32, Hash(), Equal(_entries))
{
    make_entries(16);
}

SharedStringRepo::Partition::Entry::Entry(Entry &&) noexcept = default;
SharedStringRepo::Partition::Entry::~Entry() = default;

vespalib::string
SharedStringRepo::Partition::Entry::as_string() const {
    assert(!is_free());
    return _str;
}
void
SharedStringRepo::Partition::Entry::add_ref() {
    assert(!is_free());
    ++_ref_cnt;
}
bool
SharedStringRepo::Partition::Entry::sub_ref() {
    assert(!is_free());
    return (--_ref_cnt == 0);
}

uint32_t
SharedStringRepo::Partition::resolve(const AltKey &alt_key) {
    bool count_refs = should_reclaim;
    std::lock_guard guard(_lock);
    auto pos = _hash.find(alt_key);
    if (pos != _hash.end()) {
        if (count_refs) {
            _entries[pos->idx].add_ref();
        }
        return pos->idx;
    } else {
        uint32_t idx = make_entry(alt_key);
        _hash.force_insert(Key{idx, alt_key.hash});
        return idx;
    }
}

vespalib::string
SharedStringRepo::Partition::as_string(uint32_t idx) const {
    std::lock_guard guard(_lock);
    return _entries[idx].as_string();
}

void
SharedStringRepo::Partition::copy(uint32_t idx) {
    std::lock_guard guard(_lock);
    _entries[idx].add_ref();
}

void
SharedStringRepo::Partition::reclaim(uint32_t idx) {
    std::lock_guard guard(_lock);
    Entry &entry = _entries[idx];
    if (entry.sub_ref()) {
        _hash.erase(Key{idx, entry.hash()});
        entry.fini(_free);
        _free = idx;
    }
}

void
SharedStringRepo::Partition::find_leaked_entries(size_t my_idx) const
{
    for (size_t i = 0; i < _entries.size(); ++i) {
        if (!_entries[i].is_free()) {
            size_t id = (((i << PART_BITS) | my_idx) + 1);
            LOG(warning, "leaked string id: %zu (part: %zu/%d, string: '%s')\n",
                id, my_idx, NUM_PARTS, _entries[i].str().c_str());
        }
    }
}

SharedStringRepo::Stats
SharedStringRepo::Partition::stats() const
{
    Stats stats;
    std::lock_guard guard(_lock);
    stats.active_entries = _hash.size();
    stats.total_entries = _entries.size();
    stats.max_part_usage = _hash.size();
    // memory footprint of self is counted by SharedStringRepo::stats()
    stats.memory_usage.incAllocatedBytes(sizeof(Entry) * _entries.capacity());
    stats.memory_usage.incUsedBytes(sizeof(Entry) * _entries.size());
    stats.memory_usage.incAllocatedBytes(_hash.getMemoryConsumption() - sizeof(HashType));
    stats.memory_usage.incUsedBytes(_hash.getMemoryUsed() - sizeof(HashType));
    return stats;
}

void
SharedStringRepo::Partition::make_entries(size_t hint)
{
    hint = std::max(hint, _entries.size() + 1);
    size_t want_mem = roundUp2inN(hint * sizeof(Entry));
    size_t want_entries = want_mem / sizeof(Entry);
    want_entries = std::min(want_entries, PART_LIMIT);
    assert(want_entries > _entries.size());
    _entries.reserve(want_entries);
    while (_entries.size() < _entries.capacity()) {
        _entries.emplace_back(_free);
        _free = (_entries.size() - 1);
    }
}

uint32_t
SharedStringRepo::Partition::make_entry(const AltKey &alt_key) {
    if (__builtin_expect(_free == Entry::npos, false)) {
        make_entries(_entries.size() * 2);
    }
    uint32_t idx = _free;
    _free = _entries[idx].init(alt_key);
    return idx;
}

SharedStringRepo SharedStringRepo::_repo;

SharedStringRepo::SharedStringRepo() = default;
SharedStringRepo::~SharedStringRepo()
{
    if (should_reclaim) {
        for (size_t p = 0; p < _partitions.size(); ++p) {
            _partitions[p].find_leaked_entries(p);
        }
    }
}

SharedStringRepo::Stats
SharedStringRepo::stats()
{
    Stats stats;
    stats.memory_usage.incAllocatedBytes(sizeof(SharedStringRepo));
    stats.memory_usage.incUsedBytes(sizeof(SharedStringRepo));
    for (const auto &part: _repo._partitions) {
        stats.merge(part.stats());
    }
    return stats;
}

namespace {

uint32_t
try_make_direct_id(vespalib::stringref str) noexcept {
    if ((str.size() > SharedStringRepo::FAST_DIGITS) || ((str.size() > 1) && (str[0] == '0'))) {
        return SharedStringRepo::ID_BIAS;
    } else if (str.empty()) {
        return 0;
    } else {
        uint32_t value = 0;
        for (char c: str) {
            if (!isdigit(c)) {
                return SharedStringRepo::ID_BIAS;
            } else {
                value = ((value * 10) + (c - '0'));
            }
        }
        return (value + 1);
    }
}

vespalib::string
string_from_direct_id(uint32_t id) {
    if (id == 0) {
        return {};
    } else {
        char tmp[16];
        auto res = std::to_chars(tmp, tmp + sizeof(tmp), (id - 1), 10);
        return {tmp, size_t(res.ptr - tmp)};
    }
}

}

string_id
SharedStringRepo::resolve(vespalib::stringref str) {
    uint32_t direct_id = try_make_direct_id(str);
    if (direct_id >= ID_BIAS) {
        uint64_t full_hash = XXH3_64bits(str.data(), str.size());
        uint32_t part = full_hash & PART_MASK;
        uint32_t local_hash = full_hash >> PART_BITS;
        uint32_t local_idx = _partitions[part].resolve(AltKey{str, local_hash});
        return string_id(((local_idx << PART_BITS) | part) + ID_BIAS);
    } else {
        return string_id(direct_id);
    }
}

vespalib::string
SharedStringRepo::as_string(string_id id) {
    if (id._id >= ID_BIAS) {
        uint32_t part = (id._id - ID_BIAS) & PART_MASK;
        uint32_t local_idx = (id._id - ID_BIAS) >> PART_BITS;
        return _partitions[part].as_string(local_idx);
    } else {
        return string_from_direct_id(id._id);
    }
}

string_id
SharedStringRepo::copy(string_id id) {
    if ((id._id >= ID_BIAS) && should_reclaim) {
        uint32_t part = (id._id - ID_BIAS) & PART_MASK;
        uint32_t local_idx = (id._id - ID_BIAS) >> PART_BITS;
        _partitions[part].copy(local_idx);
    }
    return id;
}

void
SharedStringRepo::reclaim(string_id id) {
    if ((id._id >= ID_BIAS) && should_reclaim) {
        uint32_t part = (id._id - ID_BIAS) & PART_MASK;
        uint32_t local_idx = (id._id - ID_BIAS) >> PART_BITS;
        _partitions[part].reclaim(local_idx);
    }
}

SharedStringRepo::Handle
SharedStringRepo::Handle::handle_from_number_slow(int64_t value) {
    char buf[24];
    auto res = std::to_chars(buf, buf + sizeof(buf), value, 10);
    return Handle(vespalib::stringref(buf, res.ptr - buf));
}

SharedStringRepo::Handles::Handles()
    : _handles()
{
}

SharedStringRepo::Handles::Handles(Handles &&rhs) noexcept
    : _handles(std::move(rhs._handles))
{
    assert(rhs._handles.empty());
}

SharedStringRepo::Handles::~Handles()
{
    if (should_reclaim) {
        for (string_id handle: _handles) {
            _repo.reclaim(handle);
        }
    }
}

}
