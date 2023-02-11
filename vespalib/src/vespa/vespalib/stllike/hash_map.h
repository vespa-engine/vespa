// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hashtable.h"
#include "hash_fun.h"
#include "select.h"

namespace vespalib {

template< typename K, typename V, typename H = vespalib::hash<K>, typename EQ = std::equal_to<>, typename M=hashtable_base::and_modulator >
class hash_map
{
public:
    using value_type = std::pair<K, V>;
    using key_type = K;
    using mapped_type = V;
    using HashTable = hashtable< K, value_type, H, EQ, Select1st<value_type>, M >;
private:
    HashTable _ht;
public:
    using iterator = typename HashTable::iterator;
    using const_iterator = typename HashTable::const_iterator;
    using insert_result = typename HashTable::insert_result;
public:
    hash_map(hash_map &&) noexcept = default;
    hash_map & operator = (hash_map &&) noexcept = default;
    hash_map(const hash_map &) = default;
    hash_map & operator = (const hash_map &) = default;
    hash_map();
    explicit hash_map(size_t reserveSize);
    hash_map(size_t reserveSize, H hasher, EQ equality);
    hash_map(std::initializer_list<value_type> input);
    ~hash_map() noexcept;
    constexpr iterator begin()                         noexcept { return _ht.begin(); }
    constexpr iterator end()                           noexcept { return _ht.end(); }
    constexpr const_iterator begin()             const noexcept { return _ht.begin(); }
    constexpr const_iterator end()               const noexcept { return _ht.end(); }
    constexpr size_t capacity()                  const noexcept { return _ht.capacity(); }
    constexpr size_t size()                      const noexcept { return _ht.size(); }
    constexpr bool empty()                       const noexcept { return _ht.empty(); }
    insert_result insert(const value_type & value) { return _ht.insert(value); }
    insert_result insert(value_type &&value) { return _ht.insert(std::move(value)); }
    template <typename InputIt>
    void insert(InputIt first, InputIt last);

    /// This gives faster iteration than can be achieved by the iterators.
    template <typename Func>
    void for_each(Func func) const { _ht.for_each(func); }
    const V & operator [] (const K & key) const { return _ht.find(key)->second; }
    V & operator [] (const K & key)             { return _ht.insert(value_type(key, V())).first->second; }
    void erase(const K & key);
    void erase(iterator it)                     { return erase(it->first); }
    void erase(const_iterator it)               { return erase(it->first); }
    constexpr iterator find(const K & key)         noexcept { return _ht.find(key); }
    constexpr size_t count(const K & key)    const noexcept { return _ht.find(key) != _ht.end() ? 1 : 0; }
    constexpr bool contains(const K & key)   const noexcept { return _ht.find(key) != end(); }
    const_iterator find(const K & key)       const noexcept { return _ht.find(key); }

    template< typename AltKey >
    const_iterator find(const AltKey & key) const {
        return _ht.template find<AltKey>(key);
    }
    template< typename AltKey>
    iterator find(const AltKey & key) noexcept {
        return _ht.template find<AltKey>(key);
    }

    void clear();
    void resize(size_t newSize);
    void swap(hash_map & rhs);
    bool operator == (const hash_map & rhs) const;
    size_t getMemoryConsumption() const;
    size_t getMemoryUsed() const;
};

template< typename K, typename V, typename H, typename EQ, typename M >
void swap(hash_map<K, V, H, EQ, M> & a, hash_map<K, V, H, EQ, M> & b)
{
    a.swap(b);
}

}
