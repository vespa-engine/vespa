// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hashtable.h"
#include "hash_fun.h"
#include <initializer_list>

namespace vespalib {

template< typename K, typename H = vespalib::hash<K>, typename EQ = std::equal_to<K>, typename M=hashtable_base::prime_modulator>
class hash_set
{
private:
    using HashTable = hashtable< K, K, H, EQ, std::_Identity<K>, M>;
    HashTable _ht;
public:
    typedef typename HashTable::iterator iterator;
    typedef typename HashTable::const_iterator const_iterator;
    typedef typename HashTable::insert_result insert_result;
public:
    hash_set(hash_set &&) = default;
    hash_set & operator = (hash_set &&) = default;
    hash_set(const hash_set &) = default;
    hash_set & operator = (const hash_set &) = default;
    hash_set(size_t reserveSize=0);
    hash_set(size_t reserveSize, const H & hasher, const EQ & equal);
    template <typename InputIterator>
    hash_set(InputIterator first, InputIterator last);

    hash_set(std::initializer_list<K> input);
    ~hash_set();
    iterator begin()                         { return _ht.begin(); }
    iterator end()                           { return _ht.end(); }
    const_iterator begin()             const { return _ht.begin(); }
    const_iterator end()               const { return _ht.end(); }
    size_t capacity()                  const { return _ht.capacity(); }
    size_t size()                      const { return _ht.size(); }
    bool empty()                       const { return _ht.empty(); }
    insert_result insert(const K & value);
    insert_result insert(K &&value);
    template<typename InputIt>
    void insert(InputIt first, InputIt last);
    void erase(const K & key);
    iterator find(const K & key)             { return _ht.find(key); }
    const_iterator find(const K & key) const { return _ht.find(key); }

    /// This gives faster iteration than can be achieved by the iterators.
    template <typename Func>
    void for_each(Func func) const { _ht.for_each(func); }

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

    void clear();
    void resize(size_t newSize);
    void swap(hash_set & rhs);

    bool operator==(const hash_set &rhs) const;

    /**
     * Get an approximate number of memory consumed by hash set. Not including
     * any data K would store outside of sizeof(K) of course.
     */
    size_t getMemoryConsumption() const;
};

template< typename K, typename H, typename EQ, typename M >
void swap(hash_set<K, H, EQ, M> & a, hash_set<K, H, EQ, M> & b)
{
    a.swap(b);
}

}

