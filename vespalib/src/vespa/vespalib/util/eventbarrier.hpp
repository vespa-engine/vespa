// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "arrayqueue.hpp"

namespace vespalib {

/**
 * Reference implementation of the 'Incremental Minimal Event Barrier'
 * algorithm. An event in this context is defined to be something that
 * happens during a time interval. An event barrier is a time interval
 * for which events may start before or end after, but not both. The
 * problem solved by the algorithm is to determine the minimal event
 * barrier starting at a given time. In other words; wait for the
 * currently active events to complete. The most natural use of this
 * algorithm would be to make a thread wait for events happening in
 * other threads to complete. The template parameter T defines how the
 * detection of a minimal event barrier should be handled. T must
 * implement the method 'void completeBarrier()' which does not throw.
 **/
template <typename T>
class EventBarrier
{
private:
    uint32_t _token;
    uint32_t _count;
    ArrayQueue<std::pair<uint32_t, T*> > _queue;

    EventBarrier(const EventBarrier &);
    EventBarrier &operator=(const EventBarrier &);

public:
    /**
     * At creation there are no active events and no pending barriers.
     **/
    EventBarrier() : _token(0), _count(0), _queue() {}

    /**
     * Obtain the current number of active events. This method is
     * intended for testing and debugging.
     *
     * @return number of active events
     **/
    uint32_t countEvents() const {
        uint32_t cnt = _count;
        for (uint32_t i = 0; i < _queue.size(); ++i) {
            cnt += _queue.peek(i).first;
        }
        return cnt;
    }

    /**
     * Obtain the current number of pending barriers. This method is
     * intended for testing and debugging.
     *
     * @return number of pending barriers
     **/
    uint32_t countBarriers() const {
        return _queue.size();
    }

    /**
     * Signal the start of an event. The value returned from this
     * method must later be passed to the completeEvent method when
     * signaling the completion of the event.
     *
     * @return opaque token identifying the started event
     **/
    uint32_t startEvent() {
        ++_count;
        return _token;
    }

    /**
     * Signal the completion of an event. The value passed to this
     * method must be the same as the return value previously obtained
     * from the startEvent method. This method will signal the
     * completion of all pending barriers that were completed by the
     * completion of this event.
     *
     * @param token opaque token identifying the completed event
     **/
    void completeEvent(uint32_t token) {
        if (token == _token) {
            --_count;
            return;
        }
        --_queue.access(_queue.size() - (_token - token)).first;
        while (!_queue.empty() && _queue.front().first == 0) {
            _queue.front().second->completeBarrier();
            _queue.pop();
        }
    }

    /**
     * Initiate the detection of the minimal event barrier starting
     * now. If this method returns false it means that no events were
     * currently active and the minimal event barrier was infinitely
     * small. If this method returns false the handler will not be
     * notified of the completion of the barrier. If this method
     * returns true it means that the started barrier is pending and
     * that the handler passed to this method will be notified of its
     * completion at a later time.
     *
     * @return true if a barrier was started, false if no events were active
     * @param handler handler notified of the completion of the barrier
     **/
    bool startBarrier(T &handler) {
        if (_count == 0 && _queue.empty()) {
            return false;
        }
        _queue.push(std::make_pair(_count, &handler));
        ++_token;
        _count = 0;
        return true;
    }
};

} // namespace vespalib

