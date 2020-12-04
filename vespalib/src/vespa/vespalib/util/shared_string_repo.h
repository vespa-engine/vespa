// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "spin_lock.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_set.hpp>
#include <xxhash.h>
#include <mutex>
#include <vector>
#include <array>

namespace vespalib {

/**
 * This class implements application-wide in-memory string
 * interning. Each string stored in the repo will be assigned a unique
 * 32-bit id that can be used by the application to check for
 * equality. The repo can never be shrunk in size, but ids can be
 * re-used when the corresponding strings are evicted from the
 * repo. Handle objects are used to track which strings are in use.
 **/
class SharedStringRepo {
private:
    static constexpr int NUM_PARTS = 64;
    static constexpr int PART_BITS = 6;
    static constexpr int PART_MASK = 0x3f;

    struct AltKey {
        vespalib::stringref str;
        uint32_t hash;
    };

    class alignas(64) Partition {
    public:
        struct Entry {
            uint32_t hash;
            uint32_t ref_cnt;
            vespalib::string str;
            Entry(const AltKey &key) noexcept : hash(key.hash), ref_cnt(1), str(key.str) {}
            void reset() {
                str.reset();
            }
            void reuse(const AltKey &key) {
                hash = key.hash;
                ref_cnt = 1;
                str = key.str;
            }
        };
        struct Key {
            uint32_t idx;
            uint32_t hash;
        };
        struct Hash {
            uint32_t operator()(const Key &key) const { return key.hash; }
            uint32_t operator()(const AltKey &key) const { return key.hash; }
        };
        struct Equal {
            const std::vector<Entry> &entries;
            Equal(const std::vector<Entry> &entries_in) : entries(entries_in) {}
            Equal(const Equal &rhs) = default;
            bool operator()(const Key &a, const Key &b) const { return (a.idx == b.idx); }
            bool operator()(const Key &a, const AltKey &b) const { return ((a.hash == b.hash) && (entries[a.idx].str == b.str)); }
        };
        using HashType = vespalib::hash_set<Key,Hash,Equal>;

    private:
        SpinLock              _lock;
        std::vector<Entry>    _entries;
        std::vector<uint32_t> _free;
        HashType              _hash;

        uint32_t make_entry(const AltKey &alt_key) {
            if (_free.empty()) {
                uint32_t idx = _entries.size();
                _entries.emplace_back(alt_key);
                return idx;
            } else {
                uint32_t idx = _free.back();
                _free.pop_back();
                _entries[idx].reuse(alt_key);
                return idx;
            }
        }

    public:
        Partition()
            : _lock(), _entries(), _free(), _hash(0, Hash(), Equal(_entries)) {}
        ~Partition();

        uint32_t resolve(const AltKey &alt_key) {
            std::lock_guard guard(_lock);
            auto pos = _hash.find(alt_key);
            if (pos != _hash.end()) {
                ++_entries[pos->idx].ref_cnt;
                return pos->idx;
            } else {
                uint32_t idx = make_entry(alt_key);
                _hash.insert(Key{idx, alt_key.hash});
                return idx;
            }
        }

        vespalib::string as_string(uint32_t idx) {
            std::lock_guard guard(_lock);
            return _entries[idx].str;
        }

        void copy(uint32_t idx) {
            std::lock_guard guard(_lock);
            ++_entries[idx].ref_cnt;
        }

        void reclaim(uint32_t idx) {
            std::lock_guard guard(_lock);
            Entry &entry = _entries[idx];
            if (--entry.ref_cnt == 0) {
                _hash.erase(Key{idx, entry.hash});
                entry.reset();
                _free.push_back(idx);
            }
        }
    };

    std::array<Partition,NUM_PARTS> _partitions;

    SharedStringRepo();
    ~SharedStringRepo();

    uint32_t resolve(vespalib::stringref str) {
        if (!str.empty()) {
            uint64_t full_hash = XXH3_64bits(str.data(), str.size());
            uint32_t part = full_hash & PART_MASK;
            uint32_t local_hash = full_hash >> PART_BITS;
            uint32_t local_idx = _partitions[part].resolve(AltKey{str, local_hash});
            return (((local_idx << PART_BITS) | part) + 1);
        } else {
            return 0;
        }
    }

    vespalib::string as_string(uint32_t id) {
        if (id != 0) {
            uint32_t part = (id - 1) & PART_MASK;
            uint32_t local_idx = (id - 1) >> PART_BITS;
            return _partitions[part].as_string(local_idx);
        } else {
            return {};
        }
    }

    uint32_t copy(uint32_t id) {
        if (id != 0) {
            uint32_t part = (id - 1) & PART_MASK;
            uint32_t local_idx = (id - 1) >> PART_BITS;
            _partitions[part].copy(local_idx);
        }
        return id;
    }

    void reclaim(uint32_t id) {
        if (id != 0) {
            uint32_t part = (id - 1) & PART_MASK;
            uint32_t local_idx = (id - 1) >> PART_BITS;
            _partitions[part].reclaim(local_idx);
        }
    }

public:
    static SharedStringRepo &get();

    class Handle {
    private:
        uint32_t _id;
    public:
        Handle() : _id(0) {}
        Handle(vespalib::stringref str) : _id(get().resolve(str)) {}
        Handle(const Handle &rhs) : _id(get().copy(rhs._id)) {}
        Handle &operator=(const Handle &rhs) {
            get().reclaim(_id);
            _id = get().copy(rhs._id);
            return *this;            
        }
        Handle(Handle &&rhs) noexcept : _id(rhs._id) {
            rhs._id = 0;
        }
        Handle &operator=(Handle &&rhs) {
            get().reclaim(_id);
            _id = rhs._id;
            rhs._id = 0;
            return *this;
        }
        bool operator==(const Handle &rhs) const { return (_id == rhs._id); }
        uint32_t id() const { return _id; }
        vespalib::string as_string() const { return get().as_string(_id); }
        ~Handle() { get().reclaim(_id); }
    };
};

}
