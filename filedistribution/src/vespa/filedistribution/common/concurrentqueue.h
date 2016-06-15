// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <queue>

#include <boost/thread/condition_variable.hpp>
#include <boost/thread/mutex.hpp>
#include <boost/thread/locks.hpp>

namespace filedistribution {

template <typename T>
class ConcurrentQueue {
public:
    typedef T value_type;
private:
    boost::condition_variable _nonEmpty;

    mutable boost::mutex _queueMutex;
    typedef boost::unique_lock<boost::mutex> UniqueLock;

    std::queue<value_type> _queue;

public:
    void push(const T& t) {
        {
            UniqueLock guard(_queueMutex);
            _queue.push(t);
        }
        _nonEmpty.notify_one();
    }

    //Assumes that value_type has nonthrow copy constructors
    const T pop() {
        UniqueLock guard(_queueMutex);
        while (_queue.empty()) {
            _nonEmpty.wait(guard);
        }
        T result = _queue.front();
        _queue.pop();
        return result;
    }

    void clear() {
        UniqueLock guard(_queueMutex);
        while (!_queue.empty()) {
            _queue.pop();
        }
    }
};

} //namespace filedistribution

