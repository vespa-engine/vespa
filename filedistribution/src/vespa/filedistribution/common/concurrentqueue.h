// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <queue>

#include <mutex>
#include <condition_variable>

namespace filedistribution {

template <typename T>
class ConcurrentQueue {
public:
    typedef T value_type;
private:
    std::condition_variable _nonEmpty;

    mutable std::mutex _queueMutex;
    typedef std::unique_lock<std::mutex> UniqueLock;

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
    bool empty() {
        UniqueLock guard(_queueMutex);
        return _queue.empty();
    }
};

} //namespace filedistribution

