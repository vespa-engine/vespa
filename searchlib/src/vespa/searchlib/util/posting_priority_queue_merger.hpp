// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "posting_priority_queue.hpp"
#include "posting_priority_queue_merger.h"

namespace search {

template <class Reader, class Writer>
void
PostingPriorityQueueMerger<Reader, Writer>::mergeHeap(Writer& writer, const IFlushToken& flush_token)
{
    while (!empty() && !flush_token.stop_requested()) {
        Reader *low = lowest();
        low->write(writer);
        low->read();
        adjust();
    }
}

template <class Reader, class Writer>
void
PostingPriorityQueueMerger<Reader, Writer>::mergeOne(Writer& writer, Reader& reader, const IFlushToken& flush_token)
{
    while (reader.isValid() && !flush_token.stop_requested()) {
        reader.write(writer);
        reader.read();
    }
}

template <class Reader, class Writer>
void
PostingPriorityQueueMerger<Reader, Writer>::mergeTwo(Writer& writer, Reader& reader1, Reader& reader2, const IFlushToken& flush_token)
{
    while (!flush_token.stop_requested()) {
        Reader &low = reader2 < reader1 ? reader2 : reader1;
        low.write(writer);
        low.read();
        if (!low.isValid())
            break;
    }
}

template <class Reader, class Writer>
void
PostingPriorityQueueMerger<Reader, Writer>::mergeSmall(Writer& writer, typename Vector::iterator ib, typename Vector::iterator ie, const IFlushToken& flush_token)
{
    while (!flush_token.stop_requested()) {
        typename Vector::iterator i = ib;
        Reader *low = i->get();
        for (++i; i != ie; ++i)
            if (*i->get() < *low)
                low = i->get();
        low->write(writer);
        low->read();
        if (!low->isValid())
            break;
    }
}

template <class Reader, class Writer>
void
PostingPriorityQueueMerger<Reader, Writer>::merge(Writer& writer, uint32_t heapLimit, const IFlushToken& flush_token)
{
    if (_vec.empty())
        return;
    for (typename Vector::iterator i = _vec.begin(), ie = _vec.end(); i != ie;
         ++i) {
        assert(i->get()->isValid());
    }
    if (_vec.size() >= heapLimit) {
        sort();
        void (PostingPriorityQueueMerger::*mergeHeapFunc)(Writer& writer, const IFlushToken& flush_token) =
            &PostingPriorityQueueMerger::mergeHeap;
        (this->*mergeHeapFunc)(writer, flush_token);
        return;
    }
    while (!flush_token.stop_requested()) {
        if (_vec.size() == 1) {
            void (*mergeOneFunc)(Writer& writer, Reader& reader, const IFlushToken& flush_token) =
                &PostingPriorityQueueMerger::mergeOne;
            (*mergeOneFunc)(writer, *_vec.front().get(), flush_token);
            _vec.clear();
            return;
        }
        if (_vec.size() == 2) {
            void (*mergeTwoFunc)(Writer& writer, Reader& reader1, Reader& reader2, const IFlushToken& flush_token) =
                &PostingPriorityQueueMerger::mergeTwo;
            (*mergeTwoFunc)(writer, *_vec[0].get(), *_vec[1].get(), flush_token);
        } else {
            void (*mergeSmallFunc)(Writer& writer,
                                   typename Vector::iterator ib,
                                   typename Vector::iterator ie,
                                   const IFlushToken& flush_token) =
                &PostingPriorityQueueMerger::mergeSmall;
            (*mergeSmallFunc)(writer, _vec.begin(), _vec.end(), flush_token);
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

}
