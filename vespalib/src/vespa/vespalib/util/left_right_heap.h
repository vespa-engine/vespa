// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {

/**
 * The LeftHeap is used to maintain a heap stored in the start (LEFT
 * side) of an array. The input to push and the output from pop is the
 * last element in the range defined by the parameters. This means
 * that a LeftHeap grows and shrinks at its right side. To access the
 * best (first) item in the heap before popping it, you need to use
 * the front function, since the placement of the best item prior to
 * popping it can vary with different implementations.
 *
 * A LeftHeap works the same way as the heap in the standard library
 * with the exception of having the comparator inverted. This means
 * that when you pop the heap, you will get the first element on the
 * heap, not the last. This marks the death of heapsort and the
 * resurrection of heap as a useful data structure.
 **/
struct LeftHeap {
    static void require_left_heap() {} // for compile-time checks
    template <typename T> static T &front(T *begin, T *) { return *begin; }
    template <typename T, typename C> static void push(T *begin, T *end, C cmp);
    template <typename T, typename C> static void pop(T *begin, T *end, C cmp);
    template <typename T, typename C> static void adjust(T *begin, T *end, C cmp);
};

/**
 * The RightHeap is used to maintain a heap stored in the end (RIGHT
 * side) of an array. The input to push and the output from pop is the
 * first element in the range defined by the parameters. This means
 * that a RightHeap grows and shrinks at its left side. The RightHeap
 * is generally harder to work with compared to the LeftHeap and is
 * only useful when you want to put heaps in both sides of an
 * array. This can be useful when there is a fixed number of elements
 * that has different priority order based on some partitioning
 * criteria that change over time.
 **/
struct RightHeap {
    static void require_right_heap() {} // for compile-time checks
    template <typename T> static T &front(T *, T *end) { return *(end - 1); }
    template <typename T, typename C> static void push(T *begin, T *end, C cmp);
    template <typename T, typename C> static void pop(T *begin, T *end, C cmp);
    template <typename T, typename C> static void adjust(T *begin, T *end, C cmp);
};

/**
 * A LeftArrayHeap is a sorted array that has the same interface as
 * the LeftHeap. This alternative could give better performance with
 * few elements.
 **/
struct LeftArrayHeap {
    static void require_left_heap() {} // for compile-time checks
    template <typename T> static T &front(T *, T *end) { return *(end - 1); }
    template <typename T, typename C> static void push(T *begin, T *end, C cmp);
    template <typename T, typename C> static void pop(T *, T *, C) {}
    template <typename T, typename C> static void adjust(T *begin, T *end, C cmp) {
        push(begin, end, cmp);
    }
};

/**
 * A RightArrayHeap is a sorted array that has the same interface as
 * the RightHeap. This alternative could give better performance with
 * few elements.
 **/
struct RightArrayHeap {
    static void require_right_heap() {} // for compile-time checks
    template <typename T> static T &front(T *begin, T *) { return *begin; }
    template <typename T, typename C> static void push(T *begin, T *end, C cmp);
    template <typename T, typename C> static void pop(T *, T *, C) {}
    template <typename T, typename C> static void adjust(T *begin, T *end, C cmp) {
        push(begin, end, cmp);
    }
};

/**
 * A LeftStdHeap adapts the heap implementation in the standard
 * library to the LeftHeap interface by inverting the comparator and
 * restricting the iterator types.
 **/
struct LeftStdHeap {
    static void require_left_heap() {} // for compile-time checks
    template <typename T> static T &front(T *begin, T *) { return *begin; }
    template <typename T, typename C> static void push(T *begin, T *end, C cmp);
    template <typename T, typename C> static void pop(T *begin, T *end, C cmp);
    template <typename T, typename C> static void adjust(T *begin, T *end, C cmp);
};

} // namespace vespalib

#include "left_right_heap.hpp"
