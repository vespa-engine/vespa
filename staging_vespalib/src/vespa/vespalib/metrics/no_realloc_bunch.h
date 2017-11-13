// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vector>
#include <assert.h>

namespace vespalib {

template <typename T>
class NoReallocBunch
{
    using MyClass = NoReallocBunch<T>;
    template<typename U>
    friend void swap(NoReallocBunch<U> &a, NoReallocBunch<U> &b);
public:
    typedef std::unique_ptr<MyClass> UP;

    NoReallocBunch();
    ~NoReallocBunch() {}

    void add(T t) {
        size_t sz = _mine.size();
        if (sz == _mine.capacity()) {
            UP next(new MyClass(_size, std::move(_more), std::move(_mine)));;
            _mine.clear();
            _mine.reserve(sz << 1);
            _more = std::move(next);
        }
        _mine.push_back(t);
        ++_size;
    }

    template<typename FUNC>
    void apply(FUNC &&func) const {
        std::vector<const MyClass *> vv;
        dffill(vv);
        for (const MyClass *p : vv) {
            for (const T& elem : p->_mine) {
                func(elem);
            }
        }
    }

    size_t size() const { return _size; }

    const T& lookup(size_t idx) const {
        assert(idx < _size);
        std::vector<const MyClass *> vv;
        dffill(vv);
        for (const MyClass *p : vv) {
            size_t sz = p->_mine.size();
            if (idx < sz) {
                return p->_mine[idx];
            }
            idx -= sz;
        }
        // assert:
        return *((T*)nullptr);
    }

private:
    void dffill(std::vector<const MyClass *> &vv) const {
        if (_more) { _more->dffill(vv); }
        vv.push_back(this);
    }

    NoReallocBunch(size_t sz, UP &&more, std::vector<T> &&mine);

    size_t _size;
    UP _more;
    std::vector<T> _mine;
};

template<typename T>
NoReallocBunch<T>::NoReallocBunch()
    : _size(0),
      _more(),
      _mine()
{
    _mine.reserve(3);
}

template<typename T>
NoReallocBunch<T>::NoReallocBunch(size_t sz, UP &&more, std::vector<T> &&mine)
    : _size(sz),
      _more(std::move(more)),
      _mine(std::move(mine))
{}

template <typename T>
void swap(NoReallocBunch<T> &a,
          NoReallocBunch<T> &b)
{
    using std::swap;
    swap(a._size, b._size);
    swap(a._mine, b._mine);
    swap(a._more, b._more);
}

} // namespace vespalib
