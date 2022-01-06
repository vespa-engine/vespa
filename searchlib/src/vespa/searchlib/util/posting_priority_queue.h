// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>

namespace search {

/*
 * Provide priority queue semantics for a set of posting readers.
 */
template <class Reader>
class PostingPriorityQueue
{
public:
    class Ref
    {
        Reader *_ref;
    public:
        Ref(Reader *ref)
            : _ref(ref)
        {
        }

        bool operator<(const Ref &rhs) const { return *_ref < *rhs._ref; }
        Reader *get() const noexcept { return _ref; }
    };

    using Vector = std::vector<Ref>;
    Vector _vec;

    PostingPriorityQueue()
        : _vec()
    {
    }

    bool empty() const { return _vec.empty(); }
    void clear() { _vec.clear(); }
    void initialAdd(Reader *it) { _vec.push_back(Ref(it)); }

    /*
     * Sort vector after a set of initial add operations, so lowest()
     * and adjust() can be used.
     */
    void sort() { std::sort(_vec.begin(), _vec.end()); }

    /*
     * Return lowest value.  Assumes vector is sorted.
     */
    Reader *lowest() const { return _vec.front().get(); }

    /*
     * The vector might no longer be sorted since the first element has changed
     * value.  Perform adjustments to make vector sorted again.
     */
    void adjust();
};

}
