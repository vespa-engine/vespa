// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "connect_thread.h"

namespace fnet {

void
ConnectThread::run()
{
    for (;;) {
        Guard guard(_lock);
        while (!_done && _queue.empty()) {
            _cond.wait(guard);
        }
        if (_done && _queue.empty()) {
            return;
        }
        assert(!_queue.empty());
        ExtConnectable *conn = _queue.front();
        _queue.pop();
        guard.unlock(); // UNLOCK
        conn->ext_connect();
    }
}

ConnectThread::ConnectThread()
    : _lock(),
      _cond(),
      _queue(),
      _done(false),
      _thread(&ConnectThread::run, this)
{
}

ConnectThread::~ConnectThread()
{
    {
        Guard guard(_lock);
        _done = true;
        _cond.notify_one();
    }
    _thread.join();
    assert(_queue.empty());
}

void
ConnectThread::connect_later(ExtConnectable *conn)
{
    Guard guard(_lock);   
    assert(!_done);
    _queue.push(conn);
    _cond.notify_one();
}

} // namespace fnet
