// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cassert>
#include <condition_variable>
#include <mutex>
#include <queue>

#define UNUSED_PARAM(p)
namespace document {

// XXX move to vespalib (or remove)
/**
 * semaphore implementation with copy/assignment functionality.
 **/
class Semaphore
{
private:
    int _count;
    int _numWaiters;
    std::mutex _lock;
    std::condition_variable _cond;
public:
    Semaphore(int count=0) : _count(count), _numWaiters(0), _lock() { }

    ~Semaphore() {
        std::lock_guard guard(_lock);
        assert(_numWaiters == 0);
    }

    bool wait(int ms) {
        bool gotSemaphore = false;
        std::unique_lock guard(_lock);
        if (_count == 0) {
            _numWaiters++;
            // we could retry if we get a signal but not the semaphore,
            // but then we risk waiting longer than expected, so
            // just ignore the return value here.
            _cond.wait_for(guard, std::chrono::milliseconds(ms));
            _numWaiters--;
        }
        if (_count > 0) {
            _count--;
            gotSemaphore = true;
        }
        assert(_count >= 0);
        return gotSemaphore;
    }

    bool wait() {
        std::unique_lock guard(_lock);
        while (_count == 0) {
            _numWaiters++;
            _cond.wait(guard);
            _numWaiters--;
        }
        _count--;
        assert(_count >= 0);
        return true;
    }

    void post() {
        std::unique_lock guard(_lock);
        assert(_count >= 0);
        _count++;
        if (_numWaiters > 0) {
            _cond.notify_one();
        }
    }
};


template <typename T, typename Q=std::queue<T> >
class QueueBase
{
public:
    QueueBase() : _lock(), _count(0), _q()   { }
    virtual ~QueueBase() { }
    size_t size()  const { return internal_size(); }
    bool   empty() const { return size() == 0; }
protected:
    std::mutex          _lock;
    document::Semaphore _count;
    Q                   _q;

    bool internal_push(const T& msg) { _q.push(msg); return true; }
    bool internal_pop(T& msg) {
        if (_q.empty()) {
            return false;
        } else {
            msg = _q.front();
            _q.pop();
            return true;
	}
    }
    size_t internal_size() const { return _q.size(); }
};

/**
 * This is a simple queue template that implements a thread safe Q by using
 * the stl::queue template. Not in any way optimized. Supports simple push and
 * pop operations together with read of size and empty check.
 **/
template <typename T, typename Q=std::queue<T> >
class Queue : public QueueBase<T, Q>
{
public:
    Queue() : QueueBase<T,Q>() { }
    bool push(const T& msg, int timeout=-1)
    {
        (void)timeout;
        bool retval;
        {
            std::lock_guard guard(this->_lock);
            retval = this->internal_push(msg);
        }
        this->_count.post();
        return retval;
    }
    bool pop(T& msg, int timeout=-1)
    {
        bool retval((timeout == -1) ?
                    this->_count.wait() :
                    this->_count.wait(timeout));
        if ( retval ) {
            std::lock_guard guard(this->_lock);
            retval = this->internal_pop(msg);
        }
        return retval;
    }
};

} // namespace document

