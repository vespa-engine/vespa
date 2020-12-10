// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

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
                // to reset or not to reset...
                // _str.reset();
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
        SpinLock              _lock;
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

        vespalib::string as_string(uint32_t idx) {
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

    // A single stand-alone string handle with ownership
    class Handle {
    private:
        uint32_t _id;
        Handle(uint32_t weak_id) : _id(get().copy(weak_id)) {}
    public:
        Handle() noexcept : _id(0) {}
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
        // NB: not lexical sorting order, but can be used in maps
        bool operator<(const Handle &rhs) const noexcept { return (_id < rhs._id); }
        bool operator==(const Handle &rhs) const noexcept { return (_id == rhs._id); }
        bool operator!=(const Handle &rhs) const noexcept { return (_id != rhs._id); }
        uint32_t id() const noexcept { return _id; }
        uint32_t hash() const noexcept { return _id; }
        vespalib::string as_string() const { return get().as_string(_id); }
        static Handle handle_from_id(uint32_t weak_id) { return Handle(weak_id); }
        static vespalib::string string_from_id(uint32_t weak_id) { return get().as_string(weak_id); }
        ~Handle() { get().reclaim(_id); }
    };

    // Read-only access to a collection of string handles
    class HandleView {
    private:
        const std::vector<uint32_t> &_handles;
    public:
        HandleView(const std::vector<uint32_t> &handles_in) : _handles(handles_in) {}
        const std::vector<uint32_t> &handles() const { return _handles; }
    };

    // A collection of string handles without ownership
    class WeakHandles {
    private:
        std::vector<uint32_t> _handles;
    public:
        WeakHandles(size_t expect_size);
        ~WeakHandles();
        void add(uint32_t handle) { _handles.push_back(handle); }
        HandleView view() const { return HandleView(_handles); }
    };

    // A collection of string handles with ownership
    class StrongHandles {
    private:
        SharedStringRepo &_repo;
        std::vector<uint32_t> _handles;        
    public:
        StrongHandles(size_t expect_size);
        StrongHandles(StrongHandles &&rhs);
        StrongHandles(const StrongHandles &) = delete;
        StrongHandles &operator=(const StrongHandles &) = delete;
        StrongHandles &operator=(StrongHandles &&) = delete;
        ~StrongHandles();
        uint32_t add(vespalib::stringref str) {
            uint32_t id = _repo.resolve(str);
            _handles.push_back(id);
            return id;
        }
        void add(uint32_t handle) {
            uint32_t id = _repo.copy(handle);
            _handles.push_back(id);
        }
        HandleView view() const { return HandleView(_handles); }
    };
};

}
