// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "string_id.h"
#include "spin_lock.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/identity.h>
#include <vespa/vespalib/stllike/hashtable.hpp>
#include <xxhash.h>
#include <mutex>
#include <vector>
#include <array>
#include <cassert>

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
public:
    struct Stats {
        size_t active_entries;
        size_t total_entries;
        Stats();
        void merge(const Stats &s);
    };

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
        class Entry {
        public:
            static constexpr uint32_t npos = -1;
        private:
            uint32_t _hash;
            uint32_t _ref_cnt;
            vespalib::string _str;
        public:
            explicit Entry(uint32_t next) noexcept
                : _hash(next), _ref_cnt(npos), _str() {}
            constexpr uint32_t hash() const noexcept { return _hash; }
            constexpr const vespalib::string &str() const noexcept { return _str; }
            constexpr bool is_free() const noexcept { return (_ref_cnt == npos); }
            uint32_t init(const AltKey &key) {
                uint32_t next = _hash;
                _hash = key.hash;
                _ref_cnt = 1;
                _str = key.str;
                return next;
            }
            void fini(uint32_t next) {
                _hash = next;
                _ref_cnt = npos;
                _str.reset();
            }
            vespalib::string as_string() const {
                assert(!is_free());
                return _str;
            }
            void add_ref() {
                assert(!is_free());
                ++_ref_cnt;
            }
            bool sub_ref() {
                assert(!is_free());
                return (--_ref_cnt == 0);
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
            bool operator()(const Key &a, const AltKey &b) const { return ((a.hash == b.hash) && (entries[a.idx].str() == b.str)); }
        };
        using HashType = hashtable<Key,Key,Hash,Equal,Identity,hashtable_base::and_modulator>;

    private:
        mutable SpinLock      _lock;
        std::vector<Entry>    _entries;
        uint32_t              _free;
        HashType              _hash;

        void make_entries(size_t hint);

        uint32_t make_entry(const AltKey &alt_key) {
            if (__builtin_expect(_free == Entry::npos, false)) {
                make_entries(_entries.size() * 2);
            }
            uint32_t idx = _free;
            _free = _entries[idx].init(alt_key);
            return idx;
        }

    public:
        Partition()
            : _lock(), _entries(), _free(Entry::npos), _hash(128, Hash(), Equal(_entries))
        {
            make_entries(64);
        }
        ~Partition();
        void find_leaked_entries(size_t my_idx) const;
        Stats stats() const;

        uint32_t resolve(const AltKey &alt_key) {
            std::lock_guard guard(_lock);
            auto pos = _hash.find(alt_key);
            if (pos != _hash.end()) {
                _entries[pos->idx].add_ref();
                return pos->idx;
            } else {
                uint32_t idx = make_entry(alt_key);
                _hash.force_insert(Key{idx, alt_key.hash});
                return idx;
            }
        }

        vespalib::string as_string(uint32_t idx) const {
            std::lock_guard guard(_lock);
            return _entries[idx].as_string();
        }

        void copy(uint32_t idx) {
            std::lock_guard guard(_lock);
            _entries[idx].add_ref();
        }

        void reclaim(uint32_t idx) {
            std::lock_guard guard(_lock);
            Entry &entry = _entries[idx];
            if (entry.sub_ref()) {
                _hash.erase(Key{idx, entry.hash()});
                entry.fini(_free);
                _free = idx;
            }
        }
    };

    std::array<Partition,NUM_PARTS> _partitions;

    SharedStringRepo();
    ~SharedStringRepo();

    string_id resolve(vespalib::stringref str) {
        if (!str.empty()) {
            uint64_t full_hash = XXH3_64bits(str.data(), str.size());
            uint32_t part = full_hash & PART_MASK;
            uint32_t local_hash = full_hash >> PART_BITS;
            uint32_t local_idx = _partitions[part].resolve(AltKey{str, local_hash});
            return string_id(((local_idx << PART_BITS) | part) + 1);
        } else {
            return {};
        }
    }

    vespalib::string as_string(string_id id) {
        if (id._id != 0) {
            uint32_t part = (id._id - 1) & PART_MASK;
            uint32_t local_idx = (id._id - 1) >> PART_BITS;
            return _partitions[part].as_string(local_idx);
        } else {
            return {};
        }
    }

    string_id copy(string_id id) {
        if (id._id != 0) {
            uint32_t part = (id._id - 1) & PART_MASK;
            uint32_t local_idx = (id._id - 1) >> PART_BITS;
            _partitions[part].copy(local_idx);
        }
        return id;
    }

    void reclaim(string_id id) {
        if (id._id != 0) {
            uint32_t part = (id._id - 1) & PART_MASK;
            uint32_t local_idx = (id._id - 1) >> PART_BITS;
            _partitions[part].reclaim(local_idx);
        }
    }

    static SharedStringRepo _repo;

public:
    static Stats stats();

    // A single stand-alone string handle with ownership
    class Handle {
    private:
        string_id _id;
        Handle(string_id weak_id) : _id(_repo.copy(weak_id)) {}
    public:
        Handle() noexcept : _id() {}
        Handle(vespalib::stringref str) : _id(_repo.resolve(str)) {}
        Handle(const Handle &rhs) : _id(_repo.copy(rhs._id)) {}
        Handle &operator=(const Handle &rhs) {
            _repo.reclaim(_id);
            _id = _repo.copy(rhs._id);
            return *this;
        }
        Handle(Handle &&rhs) noexcept : _id(rhs._id) {
            rhs._id = string_id();
        }
        Handle &operator=(Handle &&rhs) {
            _repo.reclaim(_id);
            _id = rhs._id;
            rhs._id = string_id();
            return *this;
        }
        // NB: not lexical sorting order, but can be used in maps
        bool operator<(const Handle &rhs) const noexcept { return (_id < rhs._id); }
        bool operator==(const Handle &rhs) const noexcept { return (_id == rhs._id); }
        bool operator!=(const Handle &rhs) const noexcept { return (_id != rhs._id); }
        string_id id() const noexcept { return _id; }
        uint32_t hash() const noexcept { return _id.hash(); }
        vespalib::string as_string() const { return _repo.as_string(_id); }
        static Handle handle_from_id(string_id weak_id) { return Handle(weak_id); }
        static vespalib::string string_from_id(string_id weak_id) { return _repo.as_string(weak_id); }
        ~Handle() { _repo.reclaim(_id); }
    };

    // A collection of string handles with ownership
    class Handles {
    private:
        std::vector<string_id> _handles;
    public:
        Handles();
        Handles(Handles &&rhs);
        Handles(const Handles &) = delete;
        Handles &operator=(const Handles &) = delete;
        Handles &operator=(Handles &&) = delete;
        ~Handles();
        string_id add(vespalib::stringref str) {
            string_id id = _repo.resolve(str);
            _handles.push_back(id);
            return id;
        }
        void reserve(size_t value) { _handles.reserve(value); }
        void push_back(string_id handle) {
            string_id id = _repo.copy(handle);
            _handles.push_back(id);
        }
        const std::vector<string_id> &view() const { return _handles; }
    };
};

}
