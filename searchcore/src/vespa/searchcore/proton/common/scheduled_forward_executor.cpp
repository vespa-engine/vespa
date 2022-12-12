// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "scheduled_forward_executor.h"
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <atomic>
#include <thread>
#include <cassert>

using vespalib::makeLambdaTask;

namespace proton {

class ScheduledForwardExecutor::State {
public:
    State() :
        _handle(),
        _start_success(0),
        _start_failed(0),
        _running(false)
    {}
    ~State() {
        assert( !_handle );
        assert(!isRunning());
    }
    /// Returns false if it was already running
    bool start() {
        bool already_running = _running.exchange(true);
        if (already_running) {
            _start_failed++;
        } else {
            _start_success++;
        }
        return ! already_running;
    }
    void complete() {
        bool was_running = _running.exchange(false);
        assert(was_running);
    }
    void setHandle(Handle handle) {
        _handle = std::move(handle);
    }
    void cancel() {
        _handle.reset();
        while(isRunning()) {
            std::this_thread::sleep_for(1ms);
        }
    }
private:
    bool isRunning() const { return _running.load(std::memory_order_relaxed); }
    Handle                _handle;
    std::atomic<uint64_t> _start_success;
    std::atomic<uint64_t> _start_failed;
    std::atomic<bool>     _running;
};

class ScheduledForwardExecutor::Registration : public vespalib::IDestructorCallback {
private:
    ScheduledForwardExecutor & _executor;
    uint64_t                   _key;
public:
    Registration(ScheduledForwardExecutor & executor, uint64_t key) : _executor(executor), _key(key) {}
    ~Registration() {
        _executor.cancel(_key);
    }
};

ScheduledForwardExecutor::ScheduledForwardExecutor(FNET_Transport& transport,
                                                   Executor& executor)
    : _scheduler(transport),
      _executor(executor),
      _lock(),
      _nextKey(0),
      _taskList()
{
}

ScheduledForwardExecutor::~ScheduledForwardExecutor() {
    std::lock_guard guard(_lock);
    assert(_taskList.empty());
}

bool
ScheduledForwardExecutor::cancel(uint64_t key)
{
    std::lock_guard guard(_lock);
    auto found = _taskList.find(key);
    if (found == _taskList.end()) return false;
    found->second->cancel();
    _taskList.erase(found);
    return true;
}

IScheduledExecutor::Handle
ScheduledForwardExecutor::scheduleAtFixedRate(Executor::Task::UP task,
                                              duration delay, duration interval)
{
    std::lock_guard guard(_lock);
    uint64_t key = _nextKey++;
    auto state = std::make_unique<State>();
    std::shared_ptr<Executor::Task> my_task = std::move(task);
    auto handle = _scheduler.scheduleAtFixedRate(makeLambdaTask([&, my_task = std::move(my_task), my_state=state.get()]() {
        bool start_allowed = my_state->start();
        if (start_allowed) {
            _executor.execute(makeLambdaTask([&, my_task]() {
                my_task->run();
                my_state->complete();
            }));
        }
    }), delay, interval);
    state->setHandle(std::move(handle));
    _taskList[key] = std::move(state);
    return std::make_unique<Registration>(*this, key);
}

}
