// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    mergeHeap(OUT &out, const IFlushToken& flush_token) __attribute__((noinline));

    template <class OUT>
    static void
    mergeOne(OUT &out, IN &in, const IFlushToken &flush_token) __attribute__((noinline));

    template <class OUT>
    static void
    mergeTwo(OUT &out, IN &in1, IN &in2, const IFlushToken& flush_token) __attribute__((noinline));

    template <class OUT>
    static void
    mergeSmall(OUT &out,
               typename Vector::iterator ib,
               typename Vector::iterator ie,
               const IFlushToken &flush_token)
        __attribute__((noinline));

    template <class OUT>
    void
    merge(OUT &out, uint32_t heapLimit, const IFlushToken& flush_token) __attribute__((noinline));
};


template <class IN>
void
PostingPriorityQueue<IN>::adjust()
{
    typedef typename Vector::iterator VIT;
    if (!_vec.front().get()->isValid()) {
        _vec.erase(_vec.begin());   // Iterator no longer valid
        return;
    }
    if (_vec.size() == 1)       // Only one iterator left
        return;
    // Peform binary search to find first element higher than changed value
    VIT gt = std::upper_bound(_vec.begin() + 1, _vec.end(), _vec.front());
    VIT to = _vec.begin();
    VIT from = to;
    ++from;
    Ref changed = *to;   // Remember changed value
    while (from != gt) { // Shift elements to make space for changed value
        *to = *from;
        ++from;
        ++to;
    }
    *to = changed;   // Save changed value at right location
}


template <class IN>
template <class OUT>
void
PostingPriorityQueue<IN>::mergeHeap(OUT &out, const IFlushToken& flush_token)
{
    while (!empty() && !flush_token.stop_requested()) {
        IN *low = lowest();
        low->write(out);
        low->read();
        adjust();
    }
}


template <class IN>
template <class OUT>
void
PostingPriorityQueue<IN>::mergeOne(OUT &out, IN &in, const IFlushToken& flush_token)
{
    while (in.isValid() && !flush_token.stop_requested()) {
        in.write(out);
        in.read();
    }
}

template <class IN>
template <class OUT>
void
PostingPriorityQueue<IN>::mergeTwo(OUT &out, IN &in1, IN &in2, const IFlushToken& flush_token)
{
    while (!flush_token.stop_requested()) {
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
                                     typename Vector::iterator ie,
                                     const IFlushToken& flush_token)
{
    while (!flush_token.stop_requested()) {
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
PostingPriorityQueue<IN>::merge(OUT &out, uint32_t heapLimit, const IFlushToken& flush_token)
{
    if (_vec.empty())
        return;
    for (typename Vector::iterator i = _vec.begin(), ie = _vec.end(); i != ie;
         ++i) {
        assert(i->get()->isValid());
    }
    if (_vec.size() >= heapLimit) {
        sort();
        void (PostingPriorityQueue::*mergeHeapFunc)(OUT &out, const IFlushToken& flush_token) =
            &PostingPriorityQueue::mergeHeap;
        (this->*mergeHeapFunc)(out, flush_token);
        return;
    }
    while (!flush_token.stop_requested()) {
        if (_vec.size() == 1) {
            void (*mergeOneFunc)(OUT &out, IN &in, const IFlushToken& flush_token) =
                &PostingPriorityQueue<IN>::mergeOne;
            (*mergeOneFunc)(out, *_vec.front().get(), flush_token);
            _vec.clear();
            return;
        }
        if (_vec.size() == 2) {
            void (*mergeTwoFunc)(OUT &out, IN &in1, IN &in2, const IFlushToken& flush_token) =
                &PostingPriorityQueue<IN>::mergeTwo;
            (*mergeTwoFunc)(out, *_vec[0].get(), *_vec[1].get(), flush_token);
        } else {
            void (*mergeSmallFunc)(OUT &out,
                                   typename Vector::iterator ib,
                                   typename Vector::iterator ie,
                                   const IFlushToken& flush_token) =
                &PostingPriorityQueue::mergeSmall;
            (*mergeSmallFunc)(out, _vec.begin(), _vec.end(), flush_token);
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

