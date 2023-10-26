// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <algorithm>

namespace vespalib {
/**
 * This is an ordered map that uses a vector of pair as its backing store.
 * The advantages over an std::map are that
 * - it does not allocate entries individually.
 * - it gives very good cache locality.
 * - adding element in keyorder is very cheap.
 * - so is removing in reverse key order.
 *
 * Disadvantages
 * - it is kept sorted by insertion. So that is an O(N),
 *   compared to O(log(N)) for std::map or O(1) for std::unordered_map.
 * - Same goes for erase.
 * - iterators are invalidated by the same rules as an std::vector.
 *
 **/
template< typename K, typename V, typename LT = std::less<K> >
class vector_map
{
public:
    using value_type = std::pair<K, V>;
    using key_type = K;
    using mapped_type = V;
private:
    using OrderedList = std::vector<value_type>;
    friend bool operator < (const std::pair<K, V> & a, const std::pair<K, V> & b) {
        LT lt;
        return lt(a.first, b.first);
    }
    struct KeyOrder {
        bool operator () (const std::pair<K, V> & a, const K & b) {
            LT lt;
            return lt(a.first, b);
        }
    };
    OrderedList _ht;
public:
    using iterator = typename OrderedList::iterator;
    using const_iterator = typename OrderedList::const_iterator;
public:
    vector_map() : _ht() { }
    iterator begin()                         { return _ht.begin(); }
    iterator end()                           { return _ht.end(); }
    const_iterator begin()             const { return _ht.begin(); }
    const_iterator end()               const { return _ht.end(); }
    size_t capacity()                  const { return _ht.capacity(); }
    size_t size()                      const { return _ht.size(); }
    bool empty()                       const { return _ht.empty(); }
    V & operator [] (const K & key);
    void erase(const K & key)                { return _ht.erase(find(key)); }
    void erase(iterator it)                  { return _ht.erase(it); }
    void erase(const_iterator it)            { return _ht.erase(it); }
    iterator find(const K & key)             {
        iterator f = std::lower_bound(begin(), end(), key, KeyOrder());
        LT lt;
        return ((f != end()) && !lt(key, f->first)) ? f : end();
    }
    const_iterator find(const K & key) const {
        const_iterator f = std::lower_bound(begin(), end(), key, KeyOrder());
        LT lt;
        return ((f != end()) && !lt(key, f->first)) ? f : end();
    }
    void clear()                             { _ht.clear(); }
    void reserve(size_t sz)                  { _ht.reserve(sz); }
    void swap(vector_map & rhs)              { _ht.swap(rhs._ht); }
    bool operator == (const vector_map & rhs) const;
};

template< typename K, typename V, typename LT >
void swap(vector_map<K, V, LT> & a, vector_map<K, V, LT> & b)
{
    a.swap(b);
}

template< typename K, typename V, typename LT >
V &
vector_map<K, V, LT>::operator[](const K &key) {
    LT lt;
    iterator f = std::lower_bound(begin(), end(), key, KeyOrder());
    if (f == end()) {
        _ht.template emplace_back(key, V());
        return _ht.rbegin()->second;
    } else if (lt(key, f->first)) {
        f = _ht.template emplace(f, key, V());
    }
    return f->second;
}

}


