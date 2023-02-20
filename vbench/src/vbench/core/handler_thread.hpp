// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

namespace vbench {

template <typename T>
void
HandlerThread<T>::run()
{
    for (;;) {
        std::unique_lock guard(_lock);
        while (!_done && _queue.empty()) {
            _cond.wait(guard);
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
HandlerThread<T>::HandlerThread(Handler<T> &next, init_fun_t init_fun)
    : _lock(),
      _cond(),
      _queue(),
      _next(next),
      _thread(),
      _done(false)
{
    _thread = vespalib::thread::start(*this, init_fun);
}

template <typename T>
HandlerThread<T>::~HandlerThread()
{
    if (!_done) {
        join();
    }
    assert(_queue.empty());
}

template <typename T>
void
HandlerThread<T>::handle(std::unique_ptr<T> obj)
{
    std::unique_lock guard(_lock);
    if (!_done) {
        if (_queue.empty()) {
            _cond.notify_one();
        }
        _queue.push(std::move(obj));
    }
}

template <typename T>
void
HandlerThread<T>::join()
{
    {
        std::lock_guard guard(_lock);
        _done = true;
        _cond.notify_one();
    }
    _thread.join();
}

} // namespace vbench
