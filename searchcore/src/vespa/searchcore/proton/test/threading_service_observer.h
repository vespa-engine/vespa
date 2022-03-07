// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "executor_observer.h"
#include "thread_service_observer.h"
#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/vespalib/util/sequencedtaskexecutorobserver.h>

namespace proton:: test {

class ThreadingServiceObserver : public searchcorespi::index::IThreadingService
{
private:
    searchcorespi::index::IThreadingService &_service;
    SyncableThreadServiceObserver  _master;
    ThreadServiceObserver          _index;
    ThreadExecutorObserver         _summary;
    vespalib::Executor           & _shared;
    vespalib::SequencedTaskExecutorObserver _indexFieldInverter;
    vespalib::SequencedTaskExecutorObserver _indexFieldWriter;
    vespalib::SequencedTaskExecutorObserver _attributeFieldWriter;

public:
    ThreadingServiceObserver(searchcorespi::index::IThreadingService &service);
    ~ThreadingServiceObserver() override;
    const SyncableThreadServiceObserver &masterObserver() const {
        return _master;
    }
    const ThreadServiceObserver &indexObserver() const {
        return _index;
    }
    const ThreadExecutorObserver &summaryObserver() const {
        return _summary;
    }

    void blocking_master_execute(vespalib::Executor::Task::UP task) override {
        _service.blocking_master_execute(std::move(task));
    }

    searchcorespi::index::ISyncableThreadService &master() override {
        return _master;
    }
    searchcorespi::index::IThreadService &index() override {
        return _index;
    }
    vespalib::ThreadExecutor &summary() override {
        return _summary;
    }
    vespalib::Executor &shared() override {
        return _shared;
    }
    FNET_Transport & transport() override { return _service.transport(); }
    const vespalib::Clock & clock() const override { return _service.clock(); }
    vespalib::ISequencedTaskExecutor &indexFieldInverter() override {
        return _indexFieldInverter;
    }
    vespalib::ISequencedTaskExecutor &indexFieldWriter() override {
        return _indexFieldWriter;
    }

    vespalib::ISequencedTaskExecutor &attributeFieldWriter() override {
        return _attributeFieldWriter;
    }

};

}
