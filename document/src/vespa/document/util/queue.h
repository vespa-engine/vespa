// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <queue>
#include <vespa/vespalib/util/sync.h>

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
    vespalib::Monitor _sync;

    // assignment would be unsafe
    Semaphore& operator= (const Semaphore& other);
public:
    // XXX is it really safe to just copy other._count here?
    Semaphore(const Semaphore& other) : _count(other._count), _numWaiters(0), _sync() {}

    Semaphore(int count=0) : _count(count), _numWaiters(0), _sync() { }

    virtual ~Semaphore() {
        // XXX alternative: assert(_numWaiters == 0)
        while (true) {
            vespalib::MonitorGuard guard(_sync);
            if (_numWaiters == 0) break;
            _count++;
            guard.signal();
        }
    }

    bool wait(int ms) {
        bool gotSemaphore = false;
        vespalib::MonitorGuard guard(_sync);
        if (_count == 0) {
            _numWaiters++;
            // we could retry if we get a signal but not the semaphore,
            // but then we risk waiting longer than expected, so
            // just ignore the return value here.
            guard.wait(ms);
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
        vespalib::MonitorGuard guard(_sync);
        while (_count == 0) {
            _numWaiters++;
            guard.wait();
            _numWaiters--;
        }
        _count--;
        assert(_count >= 0);
        return true;
    }

    void post() {
        vespalib::MonitorGuard guard(_sync);
        assert(_count >= 0);
        _count++;
        if (_numWaiters > 0) {
            guard.signal();
        }
    }
};


template <typename T, typename Q=std::queue<T> >
class QueueBase
{
public:
    QueueBase() : _cond(), _count(0), _q()   { }
    virtual ~QueueBase() { }
    size_t size()  const { return internal_size(); }
    bool   empty() const { return size() == 0; }
protected:
    vespalib::Monitor   _cond;
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
            vespalib::MonitorGuard guard(this->_cond);
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
            vespalib::MonitorGuard guard(this->_cond);
            retval = this->internal_pop(msg);
        }
        return retval;
    }
};

template <typename T, typename Q=std::queue<T> >
class QueueWithMax : public QueueBase<T, Q>
{
protected:
    size_t _size;
    size_t storesize() const { return _size; }
    virtual void add(const T& UNUSED_PARAM(msg)) { _size++; }
    virtual void sub(const T& UNUSED_PARAM(msg)) { _size--; }
private:
    size_t    _max;
    size_t    _lowWaterMark;
    int       _writersWaiting;
public:
    QueueWithMax(size_t max_=1000, size_t lowWaterMark_=500)
      : QueueBase<T, Q>(),
        _size(0),
        _max(max_),
        _lowWaterMark(lowWaterMark_),
        _writersWaiting(0)
    { }
    bool push(const T& msg, int timeout=-1)
    {
        bool retval=true;
        {
            vespalib::MonitorGuard guard(this->_cond);
            if (storesize() >= _max) {
                ++_writersWaiting;
                if (timeout >= 0) {
                    retval = guard.wait(timeout);
                } else {
                    guard.wait();
                }
                --_writersWaiting;
            }
            if (retval) {
                retval = internal_push(msg);
            }
	    if (retval) {
                add(msg);
	    }
        }
        if (retval) {
            this->_count.post();
        }
        return retval;
    }
    bool pop(T& msg, int timeout=-1)
    {
        bool retval((timeout == -1) ?
                    this->_count.wait() :
                    this->_count.wait(timeout));
        if ( retval ) {
            vespalib::MonitorGuard guard(this->_cond);
            retval = internal_pop(msg);
            if (retval) {
		sub(msg);
		if (_writersWaiting > 0 && storesize() < _lowWaterMark) {
		    guard.signal();
		}
            }
	}
        return retval;
    }
};

} // namespace document

