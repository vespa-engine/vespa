// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "messenger.h"
#include <vespa/vespalib/util/gate.h>

#include <vespa/log/log.h>
LOG_SETUP(".messenger");

namespace {

template<class T>
struct DeleteFunctor
{
    bool operator()(T *ptr) const
    {
        delete ptr;
        return true;
    }
};

class SyncTask : public mbus::Messenger::ITask {
private:
    vespalib::Gate &_gate;

public:
    SyncTask(vespalib::Gate &gate)
        : _gate(gate)
    { }

    ~SyncTask() {
        _gate.countDown();
    }

    void run() override { }

    uint8_t priority() const override {
        return 255;
    }
};

class AddRecurrentTask : public mbus::Messenger::ITask {
private:
    std::vector<ITask*>       &_tasks;
    mbus::Messenger::ITask::UP _task;

public:
    AddRecurrentTask(std::vector<ITask*> &tasks, mbus::Messenger::ITask::UP task)
        : _tasks(tasks),
          _task(std::move(task))
    { }

    void run() override {
        _tasks.push_back(_task.release());
    }

    uint8_t priority() const override {
        return 255;
    }
};

class DiscardRecurrentTasks : public SyncTask {
private:
    std::vector<ITask*> &_tasks;

public:
    DiscardRecurrentTasks(vespalib::Gate &gate, std::vector<ITask*> &tasks)
        : SyncTask(gate),
          _tasks(tasks)
    { }

    void run() override {
        std::for_each(_tasks.begin(), _tasks.end(), DeleteFunctor<ITask>());
        _tasks.clear();
        SyncTask::run();
    }

    uint8_t priority() const override {
        return 255;
    }
};

} // anonymous

namespace mbus {

Messenger::Messenger()
    : _lock(),
      _cond(),
      _children(),
      _queue(),
      _closed(false),
      _thread()
{}

Messenger::~Messenger()
{
    {
        std::lock_guard guard(_lock);
        _closed = true;
    }
    _cond.notify_all();
    if (_thread.joinable()) {
        _thread.join();
    }
    std::for_each(_children.begin(), _children.end(), DeleteFunctor<ITask>());
    if ( ! _queue.empty()) {
        LOG(warning,
            "Messenger shut down with pending tasks, "
            "please review shutdown logic.");
        while (!_queue.empty()) {
            delete _queue.front();
            _queue.pop();
        }
    }
}

void
Messenger::run()
{
    while (true) {
        ITask::UP task;
        {
            std::unique_lock guard(_lock);
            if (_closed) {
                break;
            }
            if (_queue.empty()) {
                _cond.wait_for(guard, 100ms);
            }
            if (!_queue.empty()) {
                task.reset(_queue.front());
                _queue.pop();
            }
        }
        if (task) {
            try {
                task->run();
            } catch (const std::exception &e) {
                LOG(warning, "An exception was thrown while running "
                    "a task; %s", e.what());
            }
        }
        for (ITask * itask : _children) {
            itask->run();
        }
    }
}

void
Messenger::addRecurrentTask(ITask::UP task)
{
    enqueue(std::make_unique<AddRecurrentTask>(_children, std::move(task)));
}

void
Messenger::discardRecurrentTasks()
{
    vespalib::Gate gate;
    enqueue(std::make_unique<DiscardRecurrentTasks>(gate, _children));
    gate.await();
}

bool
Messenger::start()
{
    _thread = std::thread([this](){run();});
    return true;
}

void
Messenger::deliverMessage(Message::UP msg, IMessageHandler &handler)
{
    handler.handleMessage(std::move(msg));
}

void
Messenger::deliverReply(Reply::UP reply, IReplyHandler &handler)
{
    handler.handleReply(std::move(reply));
}

void
Messenger::enqueue(ITask::UP task)
{
    std::unique_lock guard(_lock);
    if (!_closed) {
        _queue.push(task.release());
        if (_queue.size() == 1) {
            guard.unlock();
            _cond.notify_one();
        }
    }
}

void
Messenger::sync()
{
    vespalib::Gate gate;
    enqueue(std::make_unique<SyncTask>(gate));
    gate.await();
}

bool
Messenger::isEmpty() const
{
    std::lock_guard guard(_lock);
    return _queue.empty();
}

} // namespace mbus
