// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/hashtable.h>

namespace vespalib {

template< typename K, typename V, typename H = vespalib::hash<K>, typename EQ = std::equal_to<K>, typename M=hashtable_base::prime_modulator >
class hash_map
{
public:
    typedef std::pair<K, V> value_type;
    typedef K key_type;
    typedef V mapped_type;
private:
    typedef hashtable< K, value_type, H, EQ, std::_Select1st< value_type >, M > HashTable;
    HashTable _ht;
public:
    typedef typename HashTable::iterator iterator;
    typedef typename HashTable::const_iterator const_iterator;
    typedef typename HashTable::insert_result insert_result;
public:
    hash_map(size_t reserveSize=0) : _ht(reserveSize) { }
    iterator begin()                         { return _ht.begin(); }
    iterator end()                           { return _ht.end(); }
    const_iterator begin()             const { return _ht.begin(); }
    const_iterator end()               const { return _ht.end(); }
    size_t capacity()                  const { return _ht.capacity(); }
    size_t size()                      const { return _ht.size(); }
    bool empty()                       const { return _ht.empty(); }
    insert_result insert(const value_type & value)    { return _ht.insert(value); }
    template <typename InputIt>
    void insert(InputIt first, InputIt last);
    const V & operator [] (const K & key) const    { return _ht.find(key)->second; }
    V & operator [] (const K & key)          { return _ht.insert(value_type(key, V())).first->second; }
    void erase(const K & key)                { return _ht.erase(key); }
    void erase(iterator it)                  { return _ht.erase(it->first); }
    void erase(const_iterator it)            { return _ht.erase(it->first); }
    iterator find(const K & key)             { return _ht.find(key); }
    const_iterator find(const K & key) const { return _ht.find(key); }
    void clear()                             { _ht.clear(); }
    void resize(size_t newSize)              { _ht.resize(newSize); }
    void swap(hash_map & rhs)                { _ht.swap(rhs._ht); }
    bool operator == (const hash_map & rhs) const;
    size_t getMemoryConsumption() const      { return _ht.getMemoryConsumption(); }
    size_t getMemoryUsed() const             { return _ht.getMemoryUsed(); }
};

template <typename K, typename V, typename H, typename EQ, typename M>
bool hash_map<K, V, H, EQ, M>::operator ==(const hash_map & rhs) const {
    bool identical(rhs.size() == size());
    if (identical) {
        for(const_iterator at(begin()), mat(end()); identical && at != mat; at++) {
            const_iterator bt = rhs.find(at->first);
            identical = (bt != rhs.end()) && (*at == *bt);
        }
    }
    return identical;
}

template <typename K, typename V, typename H, typename EQ, typename M>
template <typename InputIt>
void hash_map<K, V, H, EQ, M>::insert(InputIt first, InputIt last) {
    while (first != last) {
        _ht.insert(*first);
        ++first;
    }
}

template< typename K, typename V, typename H, typename EQ, typename M >
void swap(hash_map<K, V, H, EQ, M> & a, hash_map<K, V, H, EQ, M> & b)
{
    a.swap(b);
}


}


