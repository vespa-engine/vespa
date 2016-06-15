// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once
#include <algorithm>
#include <vector>
#include <cstddef>

#ifdef __GNUC__
#define USE_STL_WORKAROUNDS 1
#endif

#ifdef USE_STL_WORKAROUNDS

namespace fast {

// STL like wrapper around Fast_Array providing multiset functionality


template <class ValueType, class Comparator>
class multiset
{
public:

    class iterator
        : public std::iterator<std::bidirectional_iterator_tag, ValueType,
                               ptrdiff_t, ValueType*, ValueType&>
    {
    public:
        iterator(multiset<ValueType, Comparator>& mset, int pos) : _myset(mset), _pos(pos) {}
        inline ValueType operator*() { return _myset._values[_pos]; }
        inline iterator& operator++() { _pos++; return *this; }
        inline iterator& operator--() { _pos--; return *this; }
        inline bool operator!=(const iterator& i2) { return i2._pos != _pos; }
        inline bool operator==(const iterator& i2) { return i2._pos == _pos; }
    protected:
        friend class multiset;
        const multiset<ValueType, Comparator>& _myset;
        int _pos;
    };

    inline multiset() : _values(), _sorted(true)  {}

    inline bool insert(ValueType& v)
    {
        _sorted = false;
        _values.push_back(v);
        return true;
    }

    inline void clear()  { _values.clear(); _sorted = true; }

    inline int size() const { return _values.size(); }

    iterator begin() { sort(); return iterator(*this, 0);  }
    iterator end()   { return iterator(*this, size()); }

protected:
    inline void sort()
    {
        if (!_sorted) {
            std::stable_sort(_values.begin(), _values.end(), Comparator());
            _sorted = true;
        }
    }

private:
    friend class iterator;
    std::vector<ValueType> _values;
    bool _sorted;
};  // end class multiset

}  // end namespace fast

#define JUNIPER_MULTISET fast::multiset
#define JUNIPER_SET      fast::multiset

#else
#include <set>
#define JUNIPER_MULTISET std::multiset
#define JUNIPER_SET      std::set
#endif

