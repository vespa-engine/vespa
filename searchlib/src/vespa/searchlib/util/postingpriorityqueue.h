// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>

namespace search
{

/*
 * Provide priority queue semantics for a set of posting inputs.
 */
template <class IN>
class PostingPriorityQueue
{
public:
    class Ref
    {
        IN *_ref;
    public:
        Ref(IN *ref)
            : _ref(ref)
        {
        }

        bool
        operator<(const Ref &rhs) const
        {
            return *_ref < *rhs._ref;
        }

        IN *
        get() const
        {
            return _ref;
        }
    };

    typedef std::vector<Ref> Vector;
    Vector _vec;

    PostingPriorityQueue()
        : _vec()
    {
    }

    bool
    empty() const
    {
        return _vec.empty();
    }

    void
    clear()
    {
        _vec.clear();
    }

    void
    initialAdd(IN *it)
    {
        _vec.push_back(Ref(it));
    }

    /*
     * Sort vector after a set of initial add operations, so lowest()
     * and adjust() can be used.
     */
    void
    sort()
    {
        std::sort(_vec.begin(), _vec.end());
    }

    /*
     * Return lowest value.  Assumes vector is sorted.
     */
    IN *
    lowest() const
    {
        return _vec.front().get();
    }

    /*
     * The vector might no longer be sorted since the first element has changed
     * value.  Perform adjustments to make vector sorted again.
     */
    void
    adjust();


    template <class OUT>
    void
    mergeHeap(OUT &out) __attribute__((noinline));

    template <class OUT>
    static void
    mergeOne(OUT &out, IN &in) __attribute__((noinline));

    template <class OUT>
    static void
    mergeTwo(OUT &out, IN &in1, IN &in2) __attribute__((noinline));

    template <class OUT>
    static void
    mergeSmall(OUT &out,
               typename Vector::iterator ib,
               typename Vector::iterator ie)
        __attribute__((noinline));

    template <class OUT>
    void
    merge(OUT &out, uint32_t heapLimit) __attribute__((noinline));
};


template <class IN>
void
PostingPriorityQueue<IN>::adjust()
{
    typedef typename Vector::iterator VIT;
    if (!_vec.front().get()->isValid()) {
        _vec.erase(_vec.begin());	// Iterator no longer valid
        return;
    }
    if (_vec.size() == 1)		// Only one iterator left
        return;
    // Peform binary search to find first element higher than changed value
    VIT gt = std::upper_bound(_vec.begin() + 1, _vec.end(), _vec.front());
    VIT to = _vec.begin();
    VIT from = to;
    ++from;
    Ref changed = *to;	 // Remember changed value
    while (from != gt) { // Shift elements to make space for changed value
        *to = *from;
        ++from;
        ++to;
    }
    *to = changed;	 // Save changed value at right location
}


template <class IN>
template <class OUT>
void
PostingPriorityQueue<IN>::mergeHeap(OUT &out)
{
    while (!empty()) {
        IN *low = lowest();
        low->write(out);
        low->read();
        adjust();
    }
}


template <class IN>
template <class OUT>
void
PostingPriorityQueue<IN>::mergeOne(OUT &out, IN &in)
{
    while (in.isValid()) {
        in.write(out);
        in.read();
    }
}

template <class IN>
template <class OUT>
void
PostingPriorityQueue<IN>::mergeTwo(OUT &out, IN &in1, IN &in2)
{
    for (;;) {
        IN &low = in2 < in1 ? in2 : in1;
        low.write(out);
        low.read();
        if (!low.isValid())
            break;
    }
}


template <class IN>
template <class OUT>
void
PostingPriorityQueue<IN>::mergeSmall(OUT &out,
                                     typename Vector::iterator ib,
                                     typename Vector::iterator ie)
{
    for (;;) {
        typename Vector::iterator i = ib;
        IN *low = i->get();
        for (++i; i != ie; ++i)
            if (*i->get() < *low)
                low = i->get();
        low->write(out);
        low->read();
        if (!low->isValid())
            break;
    }
}


template <class IN>
template <class OUT>
void
PostingPriorityQueue<IN>::merge(OUT &out, uint32_t heapLimit)
{
    if (_vec.empty())
        return;
    for (typename Vector::iterator i = _vec.begin(), ie = _vec.end(); i != ie;
         ++i) {
        assert(i->get()->isValid());
    }
    if (_vec.size() >= heapLimit) {
        sort();
        void (PostingPriorityQueue::*mergeHeapFunc)(OUT &out) =
            &PostingPriorityQueue::mergeHeap;
        (this->*mergeHeapFunc)(out);
        return;
    }
    for (;;) {
        if (_vec.size() == 1) {
            void (*mergeOneFunc)(OUT &out, IN &in) =
                &PostingPriorityQueue<IN>::mergeOne;
            (*mergeOneFunc)(out, *_vec.front().get());
            _vec.clear();
            return;
        }
        if (_vec.size() == 2) {
            void (*mergeTwoFunc)(OUT &out, IN &in1, IN &in2) =
                &PostingPriorityQueue<IN>::mergeTwo;
            (*mergeTwoFunc)(out, *_vec[0].get(), *_vec[1].get());
        } else {
            void (*mergeSmallFunc)(OUT &out,
                                   typename Vector::iterator ib,
                                   typename Vector::iterator ie) =
                &PostingPriorityQueue::mergeSmall;
            (*mergeSmallFunc)(out, _vec.begin(), _vec.end());
        }
        for (typename Vector::iterator i = _vec.begin(), ie = _vec.end();
             i != ie; ++i) {
            if (!i->get()->isValid()) {
                _vec.erase(i);
                break;
            }
        }
        for (typename Vector::iterator i = _vec.begin(), ie = _vec.end();
             i != ie; ++i) {
            assert(i->get()->isValid());
        }
        assert(!_vec.empty());
    }
}


} // namespace search

