// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hashtable.h"
#include "hash_fun.h"

namespace vespalib {

template< typename K, typename V, typename H = vespalib::hash<K>, typename EQ = std::equal_to<K>, typename M=hashtable_base::prime_modulator >
class hash_map
{
public:
    typedef std::pair<K, V> value_type;
    typedef K key_type;
    typedef V mapped_type;
    using HashTable = hashtable< K, value_type, H, EQ, std::_Select1st< value_type >, M >;
private:
    HashTable _ht;
public:
    typedef typename HashTable::iterator iterator;
    typedef typename HashTable::const_iterator const_iterator;
    typedef typename HashTable::insert_result insert_result;
public:
    hash_map(hash_map &&) = default;
    hash_map & operator = (hash_map &&) = default;
    hash_map(const hash_map &) = default;
    hash_map & operator = (const hash_map &) = default;
    hash_map(size_t reserveSize=0);
    hash_map(size_t reserveSize, H hasher, EQ equality);
    ~hash_map();
    iterator begin()                         { return _ht.begin(); }
    iterator end()                           { return _ht.end(); }
    const_iterator begin()             const { return _ht.begin(); }
    const_iterator end()               const { return _ht.end(); }
    size_t capacity()                  const { return _ht.capacity(); }
    size_t size()                      const { return _ht.size(); }
    bool empty()                       const { return _ht.empty(); }
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
    iterator find(const K & key)                { return _ht.find(key); }
    const_iterator find(const K & key)    const { return _ht.find(key); }
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
