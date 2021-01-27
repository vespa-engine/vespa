// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_maintenance_job.h"
#include "imaintenancejobrunner.h"
#include <vespa/vespalib/util/executor.h>
#include <mutex>

namespace proton {

class MaintenanceJobRunner : public IMaintenanceJobRunner
{
private:
    using Mutex = std::mutex;
    using Guard = std::lock_guard<Mutex>;

    vespalib::Executor  &_executor;
    IMaintenanceJob::UP  _job;
    bool                 _stopped;
    bool                 _queued;
    bool                 _running;
    mutable Mutex        _lock;

    void addExecutorTask();
    void runJobInExecutor();
    
public:
    typedef std::shared_ptr<MaintenanceJobRunner> SP;

    MaintenanceJobRunner(vespalib::Executor &executor, IMaintenanceJob::UP job);
    void run() override;
    void stop();
    bool isRunning() const;
    const vespalib::Executor & getExecutor() const { return _executor; }
    const IMaintenanceJob &getJob() const { return *_job; }
    IMaintenanceJob &getJob() { return *_job; }
};

} // namespace proton
