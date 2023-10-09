// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <algorithm>

namespace vespalib {

namespace { // left/right heap operations (code duplicated since we use old gcc)

template <typename T, typename C>
void left_heap_insert(T *heap, size_t pos, T value, C cmp) {
    size_t parent = (pos - 1) >> 1;
    while (pos != 0 && cmp(value, *(heap + parent))) {
        *(heap + pos) = std::move(*(heap + parent));
        pos = parent;
        parent = (parent - 1) >> 1;
    }
    *(heap + pos) = std::move(value);
}

template <typename T, typename C>
void right_heap_insert(T *heap, size_t pos, T value, C cmp) {
    size_t parent = (pos - 1) >> 1;
    while (pos != 0 && cmp(value, *(heap - parent))) {
        *(heap - pos) = std::move(*(heap - parent));
        pos = parent;
        parent = (parent - 1) >> 1;
    }
    *(heap - pos) = std::move(value);
}

template <typename T, typename C>
void left_heap_adjust(T *heap, size_t len, T value, C cmp) {
    size_t pos = 0;
    size_t child2 = 2;
    while (child2 < len) {
        if (cmp(*(heap + child2 - 1), *(heap + child2))) {
            --child2;
        }
        *(heap + pos) = std::move(*(heap + child2));
        pos = child2;
        child2 = (pos << 1) + 2;
    }
    if (child2 == len) {
        *(heap + pos) = std::move(*(heap + child2 - 1));
        pos = child2 - 1;
    }
    left_heap_insert<T,C>(heap, pos, std::move(value), cmp);
}

template <typename T, typename C>
void right_heap_adjust(T *heap, size_t len, T value, C cmp) {
    size_t pos = 0;
    size_t child2 = 2;
    while (child2 < len) {
        if (cmp(*(heap - child2 + 1), *(heap - child2))) {
            --child2;
        }
        *(heap - pos) = std::move(*(heap - child2));
        pos = child2;
        child2 = (pos << 1) + 2;
    }
    if (child2 == len) {
        *(heap - pos) = std::move(*(heap - child2 + 1));
        pos = child2 - 1;
    }
    right_heap_insert<T,C>(heap, pos, std::move(value), cmp);
}

template <typename T, typename C>
void left_heap_remove(T *heap, size_t len, T value, C cmp) {
    *(heap + len) = std::move(*heap);
    left_heap_adjust<T,C>(heap, len, std::move(value), cmp);
}

template <typename T, typename C>
void right_heap_remove(T *heap, size_t len, T value, C cmp) {
    *(heap - len) = std::move(*heap);
    right_heap_adjust<T,C>(heap, len, std::move(value), cmp);
}

} // namespace vespalib::<unnamed>

//-----------------------------------------------------------------------------

template <typename T, typename C>
void LeftHeap::push(T *begin, T *end, C cmp) {
    left_heap_insert<T,C>(begin, (end - begin - 1), std::move(*(end - 1)), cmp);
}

template <typename T, typename C>
void LeftHeap::pop(T *begin, T *end, C cmp) {
    left_heap_remove<T,C>(begin, (end - begin - 1), std::move(*(end - 1)), cmp);
}

template <typename T, typename C>
void LeftHeap::adjust(T *begin, T *end, C cmp) {
    left_heap_adjust<T,C>(begin, (end - begin), std::move(*begin), cmp);
}

//-----------------------------------------------------------------------------

template <typename T, typename C>
void RightHeap::push(T *begin, T *end, C cmp) {
    right_heap_insert<T,C>((end - 1), (end - begin - 1), std::move(*begin), cmp);
}

template <typename T, typename C>
void RightHeap::pop(T *begin, T *end, C cmp) {
    right_heap_remove<T,C>((end - 1), (end - begin - 1), std::move(*begin), cmp);
}

template <typename T, typename C>
void RightHeap::adjust(T *begin, T *end, C cmp) {
    right_heap_adjust<T,C>((end - 1), (end - begin), std::move(*(end - 1)), cmp);
}

//-----------------------------------------------------------------------------

namespace { // inverted comparator helper class

template <typename T, typename C> struct InvCmp {
    C cmp;
    InvCmp(C c) : cmp(c) {}
    bool operator()(const T &a, const T &b) const {
        return cmp(b, a);
    }
};

} // namespace vespalib::<unnamed>

//-----------------------------------------------------------------------------

template <typename T, typename C>
void LeftArrayHeap::push(T *begin, T *end, C cmp) {
    T value = std::move(*--end);
    while ((begin != end) && cmp(*(end - 1), value)) {
        *end = std::move(*(end - 1));
        --end;
    }
    *end = std::move(value);
}

//-----------------------------------------------------------------------------

template <typename T, typename C>
void RightArrayHeap::push(T *begin, T *end, C cmp) {
    T value = std::move(*begin++);
    while ((begin != end) && cmp(*begin, value)) {
        *(begin - 1) = std::move(*begin);
        ++begin;
    }
    *(begin - 1) = std::move(value);
}

//-----------------------------------------------------------------------------

template <typename T, typename C>
void LeftStdHeap::push(T *begin, T *end, C cmp) {
    std::push_heap(begin, end, InvCmp<T,C>(cmp));
}

template <typename T, typename C>
void LeftStdHeap::pop(T *begin, T *end, C cmp) {
    std::pop_heap(begin, end, InvCmp<T,C>(cmp));
}

template <typename T, typename C>
void LeftStdHeap::adjust(T *begin, T *end, C cmp) {
    std::pop_heap(begin, end, InvCmp<T,C>(cmp));
    std::push_heap(begin, end, InvCmp<T,C>(cmp));
}

//-----------------------------------------------------------------------------

} // namespace vespalib
