// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/index/i_thread_service.h>

namespace proton::test {

class ThreadExecutorObserver : public vespalib::ThreadExecutor
{
private:
    vespalib::ThreadExecutor &_service;
    uint32_t _executeCnt;

public:
    ThreadExecutorObserver(vespalib::ThreadExecutor &service)
        : _service(service),
          _executeCnt(0)
    {
    }

    uint32_t getExecuteCnt() const { return _executeCnt; }

    vespalib::Executor::Task::UP execute(vespalib::Executor::Task::UP task) override {
        ++_executeCnt;
        return _service.execute(std::move(task));
    }

    size_t getNumThreads() const override { return _service.getNumThreads(); }

    vespalib::ExecutorStats getStats() override {
        return _service.getStats();
    }

    void setTaskLimit(uint32_t taskLimit) override {
        _service.setTaskLimit(taskLimit);
    }

    uint32_t getTaskLimit() const override {
        return _service.getTaskLimit();
    }

    void wakeup() override {
        _service.wakeup();
    }
};

class ThreadServiceObserver : public searchcorespi::index::IThreadService
{
private:
    searchcorespi::index::IThreadService &_service;
    uint32_t _executeCnt;

public:
    ThreadServiceObserver(searchcorespi::index::IThreadService &service)
        : _service(service),
          _executeCnt(0)
    {
    }

    uint32_t getExecuteCnt() const { return _executeCnt; }

    vespalib::Executor::Task::UP execute(vespalib::Executor::Task::UP task) override {
        ++_executeCnt;
        return _service.execute(std::move(task));
    }
    void run(vespalib::Runnable &runnable) override {
        _service.run(runnable);
    }

    bool isCurrentThread() const override {
        return _service.isCurrentThread();
    }
    size_t getNumThreads() const override { return _service.getNumThreads(); }

    vespalib::ExecutorStats getStats() override {
        return _service.getStats();
    }

    void setTaskLimit(uint32_t taskLimit) override {
        _service.setTaskLimit(taskLimit);
    }

    uint32_t getTaskLimit() const override {
        return _service.getTaskLimit();
    }

    void wakeup() override {
        _service.wakeup();
    }
};

class SyncableThreadServiceObserver : public searchcorespi::index::ISyncableThreadService
{
private:
    searchcorespi::index::ISyncableThreadService &_service;
    uint32_t _executeCnt;

public:
    SyncableThreadServiceObserver(searchcorespi::index::ISyncableThreadService &service)
        : _service(service),
          _executeCnt(0)
    {
    }

    uint32_t getExecuteCnt() const { return _executeCnt; }

    vespalib::Executor::Task::UP execute(vespalib::Executor::Task::UP task) override {
        ++_executeCnt;
        return _service.execute(std::move(task));
    }
    void run(vespalib::Runnable &runnable) override {
        _service.run(runnable);
    }
    vespalib::Syncable &sync() override {
        _service.sync();
        return *this;
    }

    bool isCurrentThread() const override {
        return _service.isCurrentThread();
    }
    size_t getNumThreads() const override { return _service.getNumThreads(); }

    vespalib::ExecutorStats getStats() override {
        return _service.getStats();
    }

    void setTaskLimit(uint32_t taskLimit) override {
        _service.setTaskLimit(taskLimit);
    }

    uint32_t getTaskLimit() const override {
        return _service.getTaskLimit();
    }

    void wakeup() override {
        _service.wakeup();
    }
};

}
