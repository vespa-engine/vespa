// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "scheduled_forward_executor.h"
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <thread>
#include <condition_variable>
#include <cassert>

using vespalib::makeLambdaTask;

namespace proton {

class ScheduledForwardExecutor::State {
public:
    State() :
        _mutex(),
        _cond(),
        _handle(),
        _start_success(0),
        _start_failed(0),
        _running(false)
    {}
    ~State() {
        std::lock_guard guard(_mutex);
        assert( !_handle );
        assert( ! _running);
    }
    /// Returns false if it was already running
    bool start() {
        std::lock_guard guard(_mutex);
        bool already_running = _running;
        _running = true;
        if (already_running) {
            _start_failed++;
        } else {
            _start_success++;
        }
        _cond.notify_all();
        return ! already_running;
    }
    void complete() {
        std::lock_guard guard(_mutex);
        bool was_running = _running;
        _running = false;
        assert(was_running);
        _cond.notify_all();
    }
    void setHandle(Handle handle) {
        std::lock_guard guard(_mutex);
        _handle = std::move(handle);
    }
    void cancel() {
        std::unique_lock guard(_mutex);
        _handle.reset();
        while(_running) {
            _cond.wait(guard);
        }
    }
private:
    std::mutex              _mutex;
    std::condition_variable _cond;
    Handle                  _handle;
    uint64_t                _start_success;
    uint64_t                _start_failed;
    bool                    _running;
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
    std::unique_ptr<State> state;
    {
        std::lock_guard guard(_lock);
        auto found = _taskList.find(key);
        if (found == _taskList.end()) return false;
        state = std::move(found->second);
        _taskList.erase(found);
    }
    state->cancel();
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
            _executor.execute(makeLambdaTask([my_state, my_task]() {
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
