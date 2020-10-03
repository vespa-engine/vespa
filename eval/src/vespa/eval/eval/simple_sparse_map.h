// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/arrayref.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vector>
#include <cassert>

namespace vespalib::eval {

/**
 * A simple wrapper around vespalib::hash_map, using it to map a list
 * of labels (a sparse address) to an integer value (dense subspace
 * index). Labels are stored in a separate vector and the map keys
 * reference a slice of this vector. This is to avoid fragmentation
 * caused by hash keys being vectors of values. In addition, labels
 * can be specified in different ways during lookup and insert in
 * order to reduce the need for data restructuring when using the
 * map. To keep things simple, map iterators are kept away from the
 * api. This will have a minor overhead during lookup since the end
 * iterator needs to be translated to npos. All added mappings are
 * checked for uniqueness with an assert. There is no real need for
 * map entry iteration since you can just iterate the labels vector
 * directly.
 *
 * 'add_mapping' will will bind the given address to an integer value
 * equal to the current (pre-insert) size of the map. The given
 * address MUST NOT already be in the map.
 *
 * 'lookup' will return the integer value associated with the
 * given address or a special npos value if the value is not found.
 **/
class SimpleSparseMap
{
public:
    using DirectStr = ConstArrayRef<vespalib::string>;
    using DirectRef = ConstArrayRef<vespalib::stringref>;
    using IndirectRef = ConstArrayRef<const vespalib::stringref *>;

    struct Key {
        uint32_t start;
        uint32_t end;
        Key() : start(0), end(0) {}
        Key(uint32_t start_in, uint32_t end_in)
            : start(start_in), end(end_in) {}
    };

    struct Hash {
        const std::vector<vespalib::string> *labels;
        const vespalib::string &get_label(size_t i) const { return (*labels)[i]; }
        Hash() : labels(nullptr) {}
        Hash(const Hash &rhs) = default;
        Hash &operator=(const Hash &rhs) = default;
        Hash(const std::vector<vespalib::string> &labels_in) : labels(&labels_in) {}
        size_t operator()(const Key &key) const {
            size_t h = 0;
            for (size_t i = key.start; i < key.end; ++i) {
                const vespalib::string &str = get_label(i);
                h = h * 31 + hashValue(str.data(), str.size());
            }
            return h;
        }
        size_t operator()(const DirectStr &addr) const {
            size_t h = 0;
            for (const auto &str: addr) {
                h = h * 31 + hashValue(str.data(), str.size());
            }
            return h;
        }
        size_t operator()(const DirectRef &addr) const {
            size_t h = 0;
            for (const auto &str: addr) {
                h = h * 31 + hashValue(str.data(), str.size());
            }
            return h;
        }
        size_t operator()(const IndirectRef &addr) const {
            size_t h = 0;
            for (const auto *str: addr) {
                h = h * 31 + hashValue(str->data(), str->size());
            }
            return h;
        }
    };

    struct Equal {
        const std::vector<vespalib::string> *labels;
        const vespalib::string &get_label(size_t i) const { return (*labels)[i]; }
        Equal() : labels(nullptr) {}
        Equal(const Equal &rhs) = default;
        Equal &operator=(const Equal &rhs) = default;
        Equal(const std::vector<vespalib::string> &labels_in) : labels(&labels_in) {}
        bool operator()(const Key &a, const Key &b) const {
            size_t len = (a.end - a.start);
            if ((b.end - b.start) != len) {
                return false;
            }
            for (size_t i = 0; i < len; ++i) {
                if (get_label(a.start + i) != get_label(b.start + i)) {
                    return false;
                }
            }
            return true;
        }
        bool operator()(const Key &a, const DirectStr &addr) const {
            if (addr.size() != (a.end - a.start)) {
                return false;
            }
            for (size_t i = 0; i < addr.size(); ++i) {
                if (get_label(a.start + i) != addr[i]) {
                    return false;
                }
            }
            return true;
        }
        bool operator()(const Key &a, const DirectRef &addr) const {
            if (addr.size() != (a.end - a.start)) {
                return false;
            }
            for (size_t i = 0; i < addr.size(); ++i) {
                if (get_label(a.start + i) != addr[i]) {
                    return false;
                }
            }
            return true;
        }
        bool operator()(const Key &a, const IndirectRef &addr) const {
            if (addr.size() != (a.end - a.start)) {
                return false;
            }
            for (size_t i = 0; i < addr.size(); ++i) {
                if (get_label(a.start + i) != *addr[i]) {
                    return false;
                }
            }
            return true;
        }
    };

    using MapType = vespalib::hash_map<Key,uint32_t,Hash,Equal>;

private:
    std::vector<vespalib::string> _labels;
    MapType _map;

public:
    SimpleSparseMap(size_t num_mapped_dims, size_t expected_subspaces)
        : _labels(), _map(expected_subspaces * 2, Hash(_labels), Equal(_labels))
    {
        _labels.reserve(num_mapped_dims * expected_subspaces);
    }
    ~SimpleSparseMap();
    size_t size() const { return _map.size(); }
    static constexpr size_t npos() { return -1; }
    const std::vector<vespalib::string> &labels() const { return _labels; }
    void add_mapping(DirectStr addr) {
        uint32_t value = _map.size();
        uint32_t start = _labels.size();
        for (const auto &label: addr) {
            _labels.emplace_back(label);
        }
        uint32_t end = _labels.size();
        auto [ignore, was_inserted] = _map.insert(std::make_pair(Key(start, end), value));
        assert(was_inserted);
    }
    void add_mapping(DirectRef addr) {
        uint32_t value = _map.size();
        uint32_t start = _labels.size();
        for (const auto &label: addr) {
            _labels.emplace_back(label);
        }
        uint32_t end = _labels.size();
        auto [ignore, was_inserted] = _map.insert(std::make_pair(Key(start, end), value));
        assert(was_inserted);
    }
    void add_mapping(IndirectRef addr) {
        uint32_t value = _map.size();
        uint32_t start = _labels.size();
        for (const auto *label: addr) {
            _labels.emplace_back(*label);
        }
        uint32_t end = _labels.size();
        auto [ignore, was_inserted] = _map.insert(std::make_pair(Key(start, end), value));
        assert(was_inserted);
    }
    size_t lookup(DirectStr addr) const {
        auto pos = _map.find(addr);
        return (pos == _map.end()) ? npos() : pos->second;
    }
    size_t lookup(DirectRef addr) const {
        auto pos = _map.find(addr);
        return (pos == _map.end()) ? npos() : pos->second;
    }
    size_t lookup(IndirectRef addr) const {
        auto pos = _map.find(addr);
        return (pos == _map.end()) ? npos() : pos->second;
    }
};

}
