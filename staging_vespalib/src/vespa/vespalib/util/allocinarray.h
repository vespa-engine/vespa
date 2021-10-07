// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/array.h>

namespace vespalib {

/**
 * This is a class that lets you allocate memory tightly. It uses an vector interface
 * for storing objects of the same type efficiently. It also providex vector like access with [].
 * New objects are just appended to the backing vector, or if there are holes they are inserted there.
 * freed objects are not destructed upon free, but rather when the place is required again.
 * That happens either
 *   - on free if it is the last element in the backing vector.
 *   - when the AllocInArray goes out of scope.
 *   - on an explicit clear.
**/
template <typename T, typename V=vespalib::Array<T> >
class AllocInArray {
public:
    typedef uint32_t Index;
    void reserve(size_t sz) { _v.reserve(sz); }
    Index alloc(const T & v);
    void free(Index p);
    const T & operator [] (Index p) const { return _v[p]; }
    T & operator [] (Index p) { return _v[p]; }
    void clear();
    size_t size() const { return _v.size() - _free.size(); }
private:
    Index last() const { return _v.size() - 1; }
    typedef  vespalib::Array<Index> FreeList;
    V        _v;
    FreeList _free;
};

template <typename T, typename V>
typename AllocInArray<T, V>::Index
AllocInArray<T, V>::alloc(const T & v)
{
    if (_free.empty()) {
        _v.push_back(v);
        return last();
    } else {
        Index p(_free.back());
        _free.pop_back();
        _v[p] = v;
        return p;
    }
}

template <typename T, typename V>
void
AllocInArray<T, V>::free(Index p)
{
    if (p == last()) {
        _v.pop_back();
    } else if ( p < _v.size()) {
        _free.push_back(p);
    }
}

template <typename T, typename V>
void
AllocInArray<T, V>::clear()
{
    _v.clear();
    _free.clear();
}

}

