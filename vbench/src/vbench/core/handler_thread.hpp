// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

namespace vbench {

template <typename T>
void
HandlerThread<T>::run()
{
    for (;;) {
        vespalib::MonitorGuard guard(_monitor);
        while (!_done && _queue.empty()) {
            guard.wait();
        }
        if (_done && _queue.empty()) {
            return;
        }
        assert(!_queue.empty());
        std::unique_ptr<T> obj(std::move(_queue.access(0)));
        _queue.pop();
        guard.unlock(); // UNLOCK
        _next.handle(std::move(obj));
    }
}

template <typename T>
HandlerThread<T>::HandlerThread(Handler<T> &next)
    : _monitor(),
      _queue(),
      _next(next),
      _thread(*this),
      _done(false)
{
    _thread.start();
}

template <typename T>
HandlerThread<T>::~HandlerThread()
{
    join();
    assert(_queue.empty());
}

template <typename T>
void
HandlerThread<T>::handle(std::unique_ptr<T> obj)
{
    vespalib::MonitorGuard guard(_monitor);
    if (!_done) {
        if (_queue.empty()) {
            guard.signal();
        }
        _queue.push(std::move(obj));
    }
}

template <typename T>
void
HandlerThread<T>::join()
{
    {
        vespalib::MonitorGuard guard(_monitor);
        _done = true;
        guard.signal();
    }
    _thread.join();
}

} // namespace vbench
