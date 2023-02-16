// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "request_scheduler.h"
#include <vbench/core/timer.h>
#include <vespa/vespalib/util/time.h>

namespace vbench {

VESPA_THREAD_STACK_TAG(vbench_request_scheduler_thread);
VESPA_THREAD_STACK_TAG(vbench_handler_thread);

void
RequestScheduler::run()
{
    double sleepTime;
    std::vector<Request::UP> list;
    while (_queue.extract(_timer.sample(), list, sleepTime)) {
        for (size_t i = 0; i < list.size(); ++i) {
            Request::UP request = Request::UP(list[i].release());
            _dispatcher.handle(std::move(request));
        }
        list.clear();
        {
            auto guard = std::unique_lock(_lock);
            if (_may_slumber) {
                _cond.wait_for(guard, std::chrono::duration<double,std::milli>(sleepTime));
            }
        }
    }
}

RequestScheduler::RequestScheduler(CryptoEngine::SP crypto, Handler<Request> &next, size_t numWorkers)
    : _timer(),
      _proxy(next, vbench_handler_thread),
      _queue(10.0, 0.020),
      _droppedTagger(_proxy),
      _dispatcher(_droppedTagger),
      _thread(),
      _connectionPool(std::move(crypto), _timer),
      _workers(),
      _lock(),
      _cond(),
      _may_slumber(true)
{
    for (size_t i = 0; i < numWorkers; ++i) {
        _workers.push_back(std::make_unique<Worker>(_dispatcher, _proxy, _connectionPool, _timer));
    }
    _dispatcher.waitForThreads(numWorkers, 256);
}

void
RequestScheduler::abort()
{
    _queue.close();
    _queue.discard();
    {
        auto guard = std::lock_guard(_lock);
        _may_slumber = false;
        _cond.notify_all();
    }
}

void
RequestScheduler::handle(Request::UP request)
{
    double scheduledTime = request->scheduledTime();
    _queue.insert(std::move(request), scheduledTime);
}

void
RequestScheduler::start()
{
    _timer.reset();
    _thread = vespalib::thread::start(*this, vbench_request_scheduler_thread);
}

RequestScheduler &
RequestScheduler::stop()
{
    _queue.close();
    return *this;
}

void
RequestScheduler::join()
{
    _thread.join();
    _dispatcher.close();
    for (size_t i = 0; i < _workers.size(); ++i) {
        _workers[i]->join();
    }
    _proxy.join();
}

} // namespace vbench
