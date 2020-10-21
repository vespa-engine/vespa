// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/arrayref.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/stllike/hash_set.hpp>
#include <vector>
#include <cassert>
#include <type_traits>

namespace vespalib::eval {

/**
 * A map where both keys and values are arrays of some type (K and V
 * respectively). All map entries have exactly the same number of keys
 * and exactly the same number of values. Keys and values are stored
 * in separate vectors external to the map itself in order to reduce
 * memory fragmentation both by co-locating the keys and values
 * themselves and also by reducing the internal hash node size. Once
 * entries are added they cannot be removed. Keys cannot be
 * overwritten, but values can.
 **/
template <typename K, typename V, typename H = vespalib::hash<K>, typename EQ = std::equal_to<> >
class ArrayArrayMap
{
private:
    size_t _keys_per_entry;
    size_t _values_per_entry;
    std::vector<K> _keys;
    std::vector<V> _values;

public:
    size_t keys_per_entry() const { return _keys_per_entry; }
    size_t values_per_entry() const { return _values_per_entry; }

    struct Tag {
        uint32_t id;
        static constexpr uint32_t npos() { return uint32_t(-1); }
        static Tag make_invalid() { return Tag{npos()}; }
        bool valid() const { return (id != npos()); }
    };

    ConstArrayRef<K> get_keys(Tag tag) const { return {&_keys[tag.id * _keys_per_entry], _keys_per_entry}; }
    ArrayRef<V> get_values(Tag tag) { return {&_values[tag.id * _values_per_entry], _values_per_entry}; }
    ConstArrayRef<V> get_values(Tag tag) const { return {&_values[tag.id * _values_per_entry], _values_per_entry}; }

    struct MyKey {
        Tag tag;
        uint32_t hash;
    };

    template <typename T> struct AltKey {
        ConstArrayRef<T> key;
        uint32_t hash;
    };

    struct Hash {
        H hash_fun;
        template <typename T> uint32_t operator()(ConstArrayRef<T> key) const {
            uint32_t h = 0;
            for (const T &k: key) {
                if constexpr (std::is_pointer_v<T>) {
                    h = h * 31 + hash_fun(*k);
                } else {
                    h = h * 31 + hash_fun(k);
                }
            }
            return h;
        }
        uint32_t operator()(const MyKey &key) const { return key.hash; }
        template <typename T> uint32_t operator()(const AltKey<T> &key) const { return key.hash; }
    };

    struct Equal {
        const ArrayArrayMap &parent;
        EQ eq_fun;
        Equal(const ArrayArrayMap &parent_in) : parent(parent_in), eq_fun() {}
        template <typename T>
        bool operator()(const MyKey &a, const AltKey<T> &b) const {
            if ((a.hash != b.hash) || (b.key.size() != parent.keys_per_entry())) {
                return false;
            }
            auto a_key = parent.get_keys(a.tag);
            for (size_t i = 0; i < a_key.size(); ++i) {
                if constexpr (std::is_pointer_v<T>) {
                    if (!eq_fun(a_key[i], *b.key[i])) {
                        return false;
                    }
                } else {
                    if (!eq_fun(a_key[i], b.key[i])) {
                        return false;
                    }
                }
            }
            return true;
        }
        bool operator()(const MyKey &a, const MyKey &b) const {
            return operator()(a, AltKey<K>{parent.get_keys(b.tag), b.hash});
        }
    };

    using MapType = vespalib::hash_set<MyKey,Hash,Equal>;

private:
    MapType _map;
    Hash _hasher;

    template <typename T>
    Tag add_entry(ConstArrayRef<T> key, uint32_t hash) {
        uint32_t tag_id = _map.size();
        for (const auto &k: key) {
            if constexpr (std::is_pointer_v<T>) {
                _keys.push_back(*k);
            } else {
                _keys.push_back(k);
            }
        }
        _values.resize(_values.size() + _values_per_entry, V{});
        auto [pos, was_inserted] = _map.insert(MyKey{{tag_id},hash});
        assert(was_inserted);
        return Tag{tag_id};
    }

public:
    ArrayArrayMap(size_t keys_per_entry_in, size_t values_per_entry_in, size_t expected_entries)
        : _keys_per_entry(keys_per_entry_in), _values_per_entry(values_per_entry_in), _keys(), _values(),
          _map(expected_entries * 2, Hash(), Equal(*this)), _hasher()
    {
        _keys.reserve(_keys_per_entry * expected_entries);
        _values.reserve(_values_per_entry * expected_entries);
        static_assert(!std::is_pointer_v<K>, "keys cannot be pointers due to auto-deref of alt keys");
    }
    ~ArrayArrayMap();

    size_t size() const { return _map.size(); }

    template <typename T>
    Tag lookup(ConstArrayRef<T> key) const {
        auto pos = _map.find(AltKey<T>{key, _hasher(key)});
        if (pos == _map.end()) {
            return Tag::make_invalid();
        }
        return pos->tag;
    }

    template <typename T>
    Tag add_entry(ConstArrayRef<T> key) {
        return add_entry(key, _hasher(key));
    }

    template <typename T>
    std::pair<Tag,bool> lookup_or_add_entry(ConstArrayRef<T> key) {
        uint32_t hash = _hasher(key);
        auto pos = _map.find(AltKey<T>{key, hash});
        if (pos == _map.end()) {
            return {add_entry(key, hash), true};
        }
        return {pos->tag, false};
    }

    template <typename F>
    void each_entry(F &&f) const {
        for (uint32_t i = 0; i < size(); ++i) {
            f(get_keys(Tag{i}), get_values(Tag{i}));
        }
    }
};

template <typename K, typename V, typename H, typename EQ>
ArrayArrayMap<K,V,H,EQ>::~ArrayArrayMap() = default;

}
