// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>

namespace vespalib {

/**
 * Simple utility used to bundle together parameters of the same type
 * that are to be passed to some function as a vector.
 **/
template <typename T>
class Box
{
private:
    std::vector<T> _values;
public:
    Box &add(const T &t) {
        _values.push_back(t);
        return *this;
    }
    operator const std::vector<T> &() const { return _values; }
};

template <typename T>
Box<T> make_box(const T &t1) {
    return Box<T>().add(t1);
}

template <typename T>
Box<T> make_box(const T &t1, const T &t2) {
    return Box<T>().add(t1).add(t2);
}

template <typename T>
Box<T> make_box(const T &t1, const T &t2, const T &t3) {
    return Box<T>().add(t1).add(t2).add(t3);
}

template <typename T>
Box<T> make_box(const T &t1, const T &t2, const T &t3, const T &t4) {
    return Box<T>().add(t1).add(t2).add(t3).add(t4);
}

template <typename T>
Box<T> make_box(const T &t1, const T &t2, const T &t3, const T &t4, const T &t5)
{
    return Box<T>().add(t1).add(t2).add(t3).add(t4).add(t5);
}

template <typename T>
Box<T> make_box(const T &t1, const T &t2, const T &t3, const T &t4, const T &t5, const T &t6)
{
    return Box<T>().add(t1).add(t2).add(t3).add(t4).add(t5).add(t6);
}

} // namespace vespalib

