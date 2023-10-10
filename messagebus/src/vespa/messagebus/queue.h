// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <queue>
#include <cstdint>

namespace mbus {

/**
 * A simple generic queue implementation that is not thread-safe. The
 * API is similar to that of std::queue.
 **/
template <class T>
class Queue
{
private:
    std::queue<T> _queue;

public:
    /**
     * Create an empty Queue.
     **/
    Queue() : _queue() {}

    /**
     * Obtain the size of this queue. The size denotes the number of
     * elements currently on the queue.
     *
     * @return size current queue size
     **/
    uint32_t size() const {
        return _queue.size();
    }

    /**
     * Access the element located at the front of the queue. This
     * method yields undefined behavior if the queue is empty.
     *
     * @return element at the front of this queue
     **/
    T &front() {
        return _queue.front();
    }

    /**
     * Push an element to the back of this queue.
     *
     * @param val the element value
     **/
    void push(const T &val) {
        _queue.push(val);
    }

    /**
     * Pop the front element from this queue. This method yields
     * undefined behavior if the queue is empty.
     **/
    void pop() {
        _queue.pop();
    }
};

} // namespace mbus

