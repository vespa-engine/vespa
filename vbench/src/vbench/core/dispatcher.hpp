// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/thread.h>
#include <vespa/vespalib/util/time.h>

namespace vbench {

template <typename T>
Dispatcher<T>::Dispatcher(Handler<T> &fallback)
    : _fallback(fallback),
      _lock(),
      _threads(),
      _closed(false)
{
}

template <typename T>
Dispatcher<T>::~Dispatcher() = default;

template <typename T>
bool
Dispatcher<T>::waitForThreads(size_t threads, size_t pollCnt) const
{
    for (size_t i = 0; i < pollCnt; ++i) {
        if (i != 0) {
            std::this_thread::sleep_for(20ms);
        }
        {
            std::lock_guard guard(_lock);
            if (_threads.size() >= threads) {
                return true;
            }
        }
    }
    return false;
}

template <typename T>
void
Dispatcher<T>::close()
{
    std::vector<ThreadState*> threads;
    {
        std::lock_guard guard(_lock);
        std::swap(_threads, threads);
        _closed = true;
    }
    for (size_t i = 0; i < threads.size(); ++i) {
        threads[i]->gate.countDown();
    }
}

template <typename T>
void
Dispatcher<T>::handle(std::unique_ptr<T> obj)
{
    std::unique_lock guard(_lock);
    if (!_threads.empty()) {
        ThreadState *state = _threads.back();
        _threads.pop_back();
        guard.unlock();
        state->object = std::move(obj);
        state->gate.countDown();
    } else {
        bool closed = _closed;
        guard.unlock();
        if (!closed) {
            _fallback.handle(std::move(obj));
        }
    }
}

template <typename T>
std::unique_ptr<T>
Dispatcher<T>::provide()
{
    ThreadState state;
    {
        std::unique_lock guard(_lock);
        if (!_closed) {
            _threads.push_back(&state);
            guard.unlock();
            state.gate.await();
        }
    }
    return std::move(state.object);
}

} // namespace vbench
