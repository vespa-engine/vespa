// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/guard.h>
#include <queue>
#include <condition_variable>


namespace vespalib {

template <typename T>
class Queue {
private:
    std::queue<T> _q;
    std::mutex    _lock;
    std::condition_variable _cond;
    int           _waitRead;
    int           _waitWrite;
    uint32_t      _maxSize;
    bool          _closed;
    T             _nil;
    Queue(const Queue &);
    Queue &operator=(const Queue &);
public:
    Queue(const T &nil, uint32_t maxSize);
    ~Queue();
    void enqueue(const T &entry);
    void close();
    T dequeue();
};

template <typename T>
Queue<T>::Queue(const T &nil, uint32_t maxSize) :
    _q(),
    _lock(),
    _cond(),
    _waitRead(0),
    _waitWrite(0),
    _maxSize(maxSize),
    _closed(false),
    _nil(nil)
{
}

template <typename T>
Queue<T>::~Queue() = default;

template <typename T>
void Queue<T>::enqueue(const T &entry) {
    std::unique_lock guard(_lock);
    while (_q.size() >= _maxSize) {
        CounterGuard cntGuard(_waitWrite);
        _cond.wait(guard);
    }
    _q.push(entry);
    if (_waitRead > 0) {
        _cond.notify_one();
    }
}
template <typename T>
void Queue<T>::close() {
    std::unique_lock guard(_lock);
    _closed = true;
    if (_waitRead > 0) {
        _cond.notify_one();
    }
}
template <typename T>
T Queue<T>::dequeue() {
    std::unique_lock guard(_lock);
    while (_q.empty() && !_closed) {
        CounterGuard cntGuard(_waitRead);
        _cond.wait(guard);
    }
    if (_q.empty()) {
        return _nil;
    }
    T tmp = _q.front();
    _q.pop();
    if (_waitWrite > 0) {
        _cond.notify_one();
    }
    return tmp;
}

}

