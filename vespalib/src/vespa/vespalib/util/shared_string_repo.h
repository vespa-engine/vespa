// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "memoryusage.h"
#include "string_id.h"
#include "spin_lock.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/identity.h>
#include <vespa/vespalib/stllike/allocator.h>
#include <vespa/vespalib/stllike/hashtable.hpp>
#include <mutex>
#include <vector>
#include <array>
#include <cctype>
#include <limits>

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
        size_t max_part_usage;
        MemoryUsage memory_usage;
        Stats();
        void merge(const Stats &s);
        static size_t part_limit();
        [[nodiscard]] double id_space_usage() const;
    };
private:
    static constexpr uint32_t FAST_ID_MAX = 9999999;
public:
    static constexpr uint32_t FAST_DIGITS = 7;
    static constexpr uint32_t ID_BIAS = (FAST_ID_MAX + 2);
private:
    static constexpr uint32_t PART_BITS = 8;
    static constexpr uint32_t NUM_PARTS = 1 << PART_BITS;
    static constexpr uint32_t PART_MASK = NUM_PARTS - 1;
    static constexpr size_t PART_LIMIT = (std::numeric_limits<uint32_t>::max() - ID_BIAS) / NUM_PARTS;
    static const bool should_reclaim;

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
            Entry(const Entry &) = delete;
            Entry & operator =(const Entry &) = delete;
            Entry(Entry &&) noexcept;
            Entry & operator =(Entry &&) noexcept = delete;
            ~Entry();
            [[nodiscard]] constexpr uint32_t hash() const noexcept { return _hash; }
            [[nodiscard]] constexpr const vespalib::string &str() const noexcept { return _str; }
            [[nodiscard]] constexpr bool is_free() const noexcept { return (_ref_cnt == npos); }
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
            [[nodiscard]] VESPA_DLL_LOCAL vespalib::string as_string() const;
            VESPA_DLL_LOCAL void add_ref();
            VESPA_DLL_LOCAL bool sub_ref();
        };
        using EntryVector = std::vector<Entry, allocator_large<Entry>>;
        struct Key {
            uint32_t idx;
            uint32_t hash;
        };
        struct Hash {
            uint32_t operator()(const Key &key) const { return key.hash; }
            uint32_t operator()(const AltKey &key) const { return key.hash; }
        };
        struct Equal {
            const EntryVector &entries;
            explicit Equal(const EntryVector &entries_in) : entries(entries_in) {}
            Equal(const Equal &rhs) = default;
            bool operator()(const Key &a, const Key &b) const { return (a.idx == b.idx); }
            bool operator()(const Key &a, const AltKey &b) const { return ((a.hash == b.hash) && (entries[a.idx].str() == b.str)); }
        };
        using HashType = hashtable<Key,Key,Hash,Equal,Identity,hashtable_base::and_modulator>;

    private:
        mutable SpinLock   _lock;
        EntryVector        _entries;
        uint32_t           _free;
        HashType           _hash;

        VESPA_DLL_LOCAL void make_entries(size_t hint);
        VESPA_DLL_LOCAL uint32_t make_entry(const AltKey &alt_key);
    public:
        Partition();
        ~Partition();
        VESPA_DLL_LOCAL void find_leaked_entries(size_t my_idx) const;
        VESPA_DLL_LOCAL Stats stats() const;
        VESPA_DLL_LOCAL uint32_t resolve(const AltKey &alt_key);
        VESPA_DLL_LOCAL vespalib::string as_string(uint32_t idx) const;
        VESPA_DLL_LOCAL void copy(uint32_t idx);
        VESPA_DLL_LOCAL void reclaim(uint32_t idx);
    };

    std::array<Partition,NUM_PARTS> _partitions;

    SharedStringRepo();
    ~SharedStringRepo();

    string_id resolve(vespalib::stringref str);
    vespalib::string as_string(string_id id);
    string_id copy(string_id id);
    void reclaim(string_id id);

    static SharedStringRepo _repo;
public:
    static bool will_reclaim() { return should_reclaim; }
    static Stats stats();

    // A single stand-alone string handle with ownership
    class Handle {
    private:
        string_id _id;
        explicit Handle(string_id weak_id) : _id(_repo.copy(weak_id)) {}
        static Handle handle_from_number_slow(int64_t value);
    public:
        Handle() noexcept : _id() {}
        explicit Handle(vespalib::stringref str) : _id(_repo.resolve(str)) {}
        Handle(const Handle &rhs) : _id(_repo.copy(rhs._id)) {}
        Handle &operator=(const Handle &rhs) {
            string_id copy = _repo.copy(rhs._id);
            _repo.reclaim(_id);
            _id = copy;
            return *this;
        }
        Handle(Handle &&rhs) noexcept : _id(rhs._id) {
            rhs._id = string_id();
        }
        Handle &operator=(Handle &&rhs) noexcept {
            _repo.reclaim(_id);
            _id = rhs._id;
            rhs._id = string_id();
            return *this;
        }
        // NB: not lexical sorting order, but can be used in maps
        bool operator<(const Handle &rhs) const noexcept { return (_id < rhs._id); }
        bool operator==(const Handle &rhs) const noexcept { return (_id == rhs._id); }
        bool operator!=(const Handle &rhs) const noexcept { return (_id != rhs._id); }
        [[nodiscard]] string_id id() const noexcept { return _id; }
        [[nodiscard]] uint32_t hash() const noexcept { return _id.hash(); }
        [[nodiscard]] vespalib::string as_string() const { return _repo.as_string(_id); }
        static Handle handle_from_id(string_id weak_id) { return Handle(weak_id); }
        static Handle handle_from_number(int64_t value) {
            if ((value < 0) || (value > FAST_ID_MAX)) {
                return handle_from_number_slow(value);
            }
            return Handle(string_id(value + 1));
        }
        static vespalib::string string_from_id(string_id weak_id) { return _repo.as_string(weak_id); }
        ~Handle() { _repo.reclaim(_id); }
    };

    // A collection of string handles with ownership
    class Handles {
    private:
        StringIdVector _handles;
    public:
        Handles();
        Handles(Handles &&rhs) noexcept;
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
        [[nodiscard]] const StringIdVector &view() const { return _handles; }
    };

    // Used by search::tensor::TensorBufferOperations
    static string_id unsafe_copy(string_id id) { return _repo.copy(id); }
    static void unsafe_reclaim(string_id id) { return _repo.reclaim(id); }
};

}
