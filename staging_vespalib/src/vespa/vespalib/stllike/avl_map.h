// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/avl.h>

namespace vespalib {

template< typename K, typename V, typename EQ = std::equal_to<K> >
class avl_map
{
public:
    typedef std::pair<K, V> value_type;
    typedef K key_type;
    typedef V mapped_type;
private:
    typedef avl< K, value_type, EQ, std::_Select1st< value_type > > Avl;
    Avl _avl;
public:
    typedef typename Avl::iterator iterator;
    typedef typename Avl::const_iterator const_iterator;
public:
    avl_map(size_t reserveSize=0) : _avl(reserveSize) { }
    iterator begin()                         { return _avl.begin(); }
    iterator end()                           { return _avl.end(); }
    const_iterator begin()             const { return _avl.begin(); }
    const_iterator end()               const { return _avl.end(); }
    size_t capacity()                  const { return _avl.capacity(); }
    size_t size()                      const { return _avl.size(); }
    bool empty()                       const { return _avl.empty(); }
    void insert(const value_type & value)    { return _avl.insert(value); }
    template <typename InputIt>
    void insert(InputIt first, InputIt last);
    V & operator [] (const K & key) const    { return *_avl.find(key)->second; }
    V & operator [] (const K & key);
    void erase(const K & key)                { return _avl.erase(key); }
    iterator find(const K & key)             { return _avl.find(key); }
    const_iterator find(const K & key) const { return _avl.find(key); }
    void clear()                             { _avl.clear(); }
    void resize(size_t newSize)              { _avl.resize(newSize); }
    void swap(avl_map & rhs)                { _avl.swap(rhs._avl); }
};

template <typename K, typename V, typename H, typename EQ>
template <typename InputIt>
void avl_map<K, V, H, EQ>::insert(InputIt first, InputIt last) {
    while (first != last) {
        _avl.insert(*first);
        ++first;
    }
}

template< typename K, typename V, typename H, typename EQ >
V & avl_map<K, V, H, EQ>::operator [] (const K & key)
{
    iterator found = _avl.find(key);
    if (found != _avl.end()) {
        return found->second;
    }
    insert(value_type(key, V()));
    return _avl.find(key)->second;
}

}


