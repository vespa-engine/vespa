// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "executor_observer.h"
#include "thread_service_observer.h"

#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/vespalib/util/sequencedtaskexecutorobserver.h>

namespace proton::test {

class ThreadingServiceObserver : public searchcorespi::index::IThreadingService {
private:
    searchcorespi::index::IThreadingService& _service;
    SyncableThreadServiceObserver            _main;
    ThreadServiceObserver                    _index;
    ThreadExecutorObserver                   _summary;
    vespalib::Executor&                      _shared;
    vespalib::SequencedTaskExecutorObserver  _field_writer;

public:
    ThreadingServiceObserver(searchcorespi::index::IThreadingService& service);
    ~ThreadingServiceObserver() override;
    [[nodiscard]] const SyncableThreadServiceObserver& main_observer() const noexcept { return _main; }
    [[nodiscard]] const ThreadServiceObserver& index_observer() const noexcept { return _index; }
    [[nodiscard]] const ThreadExecutorObserver& summary_observer() const noexcept { return _summary; }

    void blocking_main_execute(vespalib::Executor::Task::UP task) noexcept override {
        _service.blocking_main_execute(std::move(task));
    }

    [[nodiscard]] searchcorespi::index::ISyncableThreadService& main() noexcept override { return _main; }
    [[nodiscard]] searchcorespi::index::IThreadService& index() noexcept override { return _index; }
    [[nodiscard]] vespalib::ThreadExecutor& summary() noexcept override { return _summary; }
    [[nodiscard]] vespalib::Executor& shared() noexcept override { return _shared; }
    [[nodiscard]] FNET_Transport& transport() noexcept override { return _service.transport(); }
    [[nodiscard]] vespalib::ISequencedTaskExecutor& field_writer() noexcept override { return _field_writer; }
};

} // namespace proton::test
