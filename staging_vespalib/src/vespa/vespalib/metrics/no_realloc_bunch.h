// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vector>

namespace vespalib {

template <typename T>
class NoReallocBunch
{
    using MyClass = NoReallocBunch<T>;
public:
    typedef std::unique_ptr<MyClass> UP;

    NoReallocBunch();
    ~NoReallocBunch() {}
 
    void add(T t) {
        size_t sz = _mine.size();
        if (sz == _mine.capacity()) {
            UP next(new MyClass(std::move(_more), std::move(_mine)));;
            _mine.clear();
            _mine.reserve(sz << 1);
            _more = std::move(next);
        }
        _mine.push_back(t);
        ++_size;
    }

    template<typename FUNC>
    void apply(FUNC &&func) {
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

    int lookup(const T& value) const {
        int idx = 0;
        std::vector<const MyClass *> vv;
        dffill(vv);
        for (const MyClass *p : vv) {
            for (const T& elem : p->_mine) {
                if (elem == value) {
                    return idx;
                }
                ++idx;
            }
        }
        return -1;
    }

private:
    void dffill(std::vector<const MyClass *> &vv) const {
        if (_more) { _more->dffill(vv); }
        vv.push_back(this);
    }

    NoReallocBunch(UP &&more, std::vector<T> &&mine);

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
NoReallocBunch<T>::NoReallocBunch(UP &&more, std::vector<T> &&mine)
    : _size(mine.size()),
      _more(std::move(more)),
      _mine(std::move(mine))
{}

} // namespace vespalib
