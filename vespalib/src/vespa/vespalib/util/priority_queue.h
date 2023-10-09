// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <functional>
#include <algorithm>
#include "left_right_heap.h"

namespace vespalib {

/**
 * A priority queue that orders its elements according to the less
 * operator by default. The front element may be modified in place. If
 * the front element is modified, the adjust function must be called
 * afterward to restore priority order. The priority ordering can be
 * specified with a second template parameter. The any and pop_any
 * functions can be used to access and remove the element that is
 * cheapest to remove. Note that if you change the any element, you
 * must pop it afterward. Alternative heap implementations can be
 * specified with a third template parameter. Only left heaps are
 * supported. Please refer to the left-right heap benchmarks to figure
 * out what implementation fits your case best.
 **/
template <typename T, typename C = std::less<T>, typename H = LeftHeap>
class PriorityQueue
{
private:
    C              _cmp;
    std::vector<T> _data;

public:
    PriorityQueue() : _cmp(), _data() { H::require_left_heap(); }
    PriorityQueue(C cmp) : _cmp(cmp), _data() { H::require_left_heap(); }
    bool empty() const { return _data.empty(); }
    size_t size() const { return _data.size(); }
    void push(const T &item) {
        _data.push_back(item);
        H::template push<T, C>(&_data[0], &_data[size()], _cmp);
    }
    void push(T &&item) {
        _data.push_back(std::move(item));
        H::template push<T, C>(&_data[0], &_data[size()], _cmp);
    }
    T &front() {
        return H::template front<T>(&_data[0], &_data[size()]);
    }
    void adjust() {
        H::template adjust<T,C>(&_data[0], &_data[size()], _cmp);
    }
    void pop_front() {
        H::template pop<T, C>(&_data[0], &_data[size()], _cmp);
        _data.pop_back();
    }
    T &any() { return _data.back(); }
    void pop_any() { _data.pop_back(); }
    void reserve(size_t sz) { _data.reserve(sz); }
};

} // namespace vespalib

