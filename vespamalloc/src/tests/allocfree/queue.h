// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/guard.h>
#include <vespa/vespalib/util/sync.h>
#include <queue>

namespace vespalib {

template <typename T>
class Queue {
private:
    std::queue<T> _q;
    Monitor       _cond;
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
    _cond(),
    _waitRead(0),
    _waitWrite(0),
    _maxSize(maxSize),
    _closed(false),
    _nil(nil)
{
}

template <typename T>
Queue<T>::~Queue()
{
}

template <typename T>
void Queue<T>::enqueue(const T &entry) {
    MonitorGuard guard(_cond);
    while (_q.size() >= _maxSize) {
        CounterGuard cntGuard(_waitWrite);
        guard.wait();
    }
    _q.push(entry);
    if (_waitRead > 0) {
        guard.signal();
    }
}
template <typename T>
void Queue<T>::close() {
    MonitorGuard guard(_cond);
    _closed = true;
    if (_waitRead > 0) {
        guard.signal();
    }
}
template <typename T>
T Queue<T>::dequeue() {
    MonitorGuard guard(_cond);
    while (_q.empty() && !_closed) {
        CounterGuard cntGuard(_waitRead);
        guard.wait();
    }
    if (_q.empty()) {
        return _nil;
    }
    T tmp = _q.front();
    _q.pop();
    if (_waitWrite > 0) {
        guard.signal();
    }
    return tmp;
}

}

