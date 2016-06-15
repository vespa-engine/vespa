// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/hashtable.h>
#include <initializer_list>

namespace vespalib {

template< typename K, typename H = vespalib::hash<K>, typename EQ = std::equal_to<K>, typename M=hashtable_base::prime_modulator>
class hash_set
{
private:
    typedef hashtable< K, K, H, EQ, std::_Identity<K>, M> HashTable;
    HashTable _ht;
public:
    typedef typename HashTable::iterator iterator;
    typedef typename HashTable::const_iterator const_iterator;
    typedef typename HashTable::insert_result insert_result;
public:
    hash_set(size_t reserveSize=0) : _ht(reserveSize) { }
    hash_set(size_t reserveSize, const H & hasher, const EQ & equal) : _ht(reserveSize, hasher, equal) { }
    template <typename InputIterator>
    hash_set(InputIterator first, InputIterator last)
        : _ht(0)
    {
        insert(first, last);
    }
    hash_set(std::initializer_list<K> input)
        : _ht(0)
    {
        insert(input.begin(), input.end());
    }
    iterator begin()                         { return _ht.begin(); }
    iterator end()                           { return _ht.end(); }
    const_iterator begin()             const { return _ht.begin(); }
    const_iterator end()               const { return _ht.end(); }
    size_t capacity()                  const { return _ht.capacity(); }
    size_t size()                      const { return _ht.size(); }
    bool empty()                       const { return _ht.empty(); }
    insert_result insert(const K & value)    { return _ht.insert(value); }
    template<typename InputIt>
    void insert(InputIt first, InputIt last) {
        _ht.resize(last-first + capacity());
        for(;first < last; first++) {
            _ht.insert(*first);
        }
    }
    void erase(const K & key)                { return _ht.erase(key); }
    void erase(const iterator & it)          { return _ht.erase(it); }
    iterator find(const K & key)             { return _ht.find(key); }
    const_iterator find(const K & key) const { return _ht.find(key); }

    template< typename AltKey, typename AltExtract, typename AltHash, typename AltEqual >
    const_iterator find(const AltKey & key) const { return _ht.template find<AltKey, AltExtract, AltHash, AltEqual>(key); }

    template< typename AltKey, typename AltExtract, typename AltHash, typename AltEqual >
    iterator find(const AltKey & key) { return _ht.template find<AltKey, AltExtract, AltHash, AltEqual>(key); }

    template< typename AltKey, typename AltExtract, typename AltHash, typename AltEqual >
    const_iterator find(const AltKey & key, const AltExtract & altExtract) const {
        return _ht.template find<AltKey, AltExtract, AltHash, AltEqual>(key, altExtract);
    }

    template< typename AltKey, typename AltExtract, typename AltHash, typename AltEqual >
    iterator find(const AltKey & key, const AltExtract & altExtract) {
        return _ht.template find<AltKey, AltExtract, AltHash, AltEqual>(key, altExtract);
    }

    void clear()                             { _ht.clear(); }
    void resize(size_t newSize)              { _ht.resize(newSize); }
    void swap(hash_set & rhs)                { _ht.swap(rhs._ht); }

    bool operator==(const hash_set &rhs) const {
        bool equal = (size() == rhs.size());
        if (equal) {
            for (auto itr = begin(), endItr = end(); equal && itr != endItr; ++itr) {
                equal = (rhs.find(*itr) != rhs.end());
            }
        }
        return equal;
    }

    /**
     * Get an approximate number of memory consumed by hash set. Not including
     * any data K would store outside of sizeof(K) of course.
     */
    size_t getMemoryConsumption() const { return _ht.getMemoryConsumption(); }
};

template< typename K, typename H, typename EQ, typename M >
void swap(hash_set<K, H, EQ, M> & a, hash_set<K, H, EQ, M> & b)
{
    a.swap(b);
}

}

