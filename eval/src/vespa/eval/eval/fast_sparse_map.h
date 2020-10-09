// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/arrayref.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vector>
#include <xxhash.h>
#include <type_traits>

namespace vespalib::eval {

/**
 * A wrapper around vespalib::hash_map, using it to map a list of
 * labels (a sparse address) to an integer value (dense subspace
 * index). Labels are stored in a separate vector to avoid
 * fragmentation caused by hash keys being vectors of values. Labels
 * can be specified in different ways during lookup and insert in
 * order to reduce the need for data restructuring when used to
 * integrate with the Value api. All labels are stored with a 64-bit
 * hash. This hash is used as label equality (assuming no
 * collisions). A order-sensitive 64bit hash constructed from
 * individual label hashes is used for address equality (also assuming
 * no collisions). The hash algorithm currently used is XXH3.
 *
 * 'add_mapping' will will bind the given address to an integer value
 * equal to the current (pre-insert) size of the map. The given
 * address MUST NOT already be in the map.
 *
 * 'lookup' will return the integer value associated with the
 * given address or a special npos value if the value is not found.
 **/
class FastSparseMap
{
public:
    static uint64_t hash_label(const vespalib::string &str) {
        return XXH3_64bits(str.data(), str.size());
    }
    static uint64_t hash_label(vespalib::stringref str) {
        return XXH3_64bits(str.data(), str.size());
    }
    static uint64_t hash_label(const vespalib::stringref *str) {
        return XXH3_64bits(str->data(), str->size());
    }

    struct HashedLabel {
        vespalib::string label;
        uint64_t hash;
        HashedLabel() : label(), hash(0) {}
        HashedLabel(const HashedLabel &rhs) = default;
        HashedLabel &operator=(const HashedLabel &rhs) = default;
        HashedLabel(HashedLabel &&rhs) = default;
        HashedLabel &operator=(HashedLabel &&rhs) = default;
        HashedLabel(const vespalib::string &str) : label(str), hash(hash_label(str)) {}
        HashedLabel(vespalib::stringref str) : label(str), hash(hash_label(str)) {}
        HashedLabel(const vespalib::stringref *str) : label(*str), hash(hash_label(*str)) {}
    };

    static uint64_t hash_label(const HashedLabel &label) {
        return label.hash;
    }

    struct Key {
        uint64_t hash;
        Key() : hash(0) {}
        Key(uint64_t hash_in)
            : hash(hash_in) {}
    } __attribute__((packed,aligned(4)));

    struct Hash {
        uint64_t operator()(const Key &key) const { return key.hash; }
        uint64_t operator()(uint64_t hash) const { return hash; }
    };

    struct Equal {
        bool operator()(const Key &a, uint64_t b) const { return (a.hash == b); }
        bool operator()(const Key &a, const Key &b) const { return (a.hash == b.hash); }
    };

    using MapType = vespalib::hash_map<Key,uint32_t,Hash,Equal>;

private:
    size_t _num_dims;
    std::vector<HashedLabel> _labels;
    MapType _map;

public:
    FastSparseMap(size_t num_dims_in, size_t expected_subspaces)
        : _num_dims(num_dims_in), _labels(), _map(expected_subspaces * 2)
    {
        static_assert(std::is_same_v<XXH64_hash_t, uint64_t>);
        _labels.reserve(_num_dims * expected_subspaces);
    }
    ~FastSparseMap();
    size_t size() const { return _map.size(); }
    size_t num_dims() const { return _num_dims; }
    static constexpr size_t npos() { return -1; }
    const std::vector<HashedLabel> &labels() const { return _labels; }

    ConstArrayRef<HashedLabel> make_addr(uint32_t index) const {
        return ConstArrayRef<HashedLabel>(&_labels[index * _num_dims], _num_dims);
    }

    template <typename T>
    uint64_t hash_addr(ConstArrayRef<T> addr) const {
        uint64_t h = 0;
        for (const auto &label: addr) {
            h = 31 * h + hash_label(label);
        }
        return h;
    }

    template <typename T>
    void add_mapping(ConstArrayRef<T> addr, uint64_t hash) {
        uint32_t value = _map.size();
        for (const auto &label: addr) {
            _labels.emplace_back(label);
        }
        _map.insert(std::make_pair(Key(hash), value));
    }

    template <typename T>
    void add_mapping(ConstArrayRef<T> addr) {
        uint64_t h = 0;
        uint32_t value = _map.size();
        for (const auto &label: addr) {
            _labels.emplace_back(label);
            h = 31 * h + hash_label(_labels.back());
        }
        _map.insert(std::make_pair(Key(h), value));
    }

    size_t lookup(uint64_t hash) const {
        auto pos = _map.find(hash);
        return (pos == _map.end()) ? npos() : pos->second;
    }

    template <typename T>
    size_t lookup(ConstArrayRef<T> addr) const {
        return lookup(hash_addr(addr));
    }

    template <typename F>
    void each_map_entry(F &&f) const {
        _map.for_each([&](const auto &entry)
                      {
                          f(entry.second, entry.first.hash);
                      });
    }
};

}
