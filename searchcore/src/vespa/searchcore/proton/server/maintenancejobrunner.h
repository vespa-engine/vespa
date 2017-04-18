// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/threadstackexecutorbase.h>
#include "i_maintenance_job.h"
#include "imaintenancejobrunner.h"
#include <mutex>

namespace proton
{

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

    MaintenanceJobRunner(vespalib::Executor &executor,
                         IMaintenanceJob::UP job);
    virtual void run() override;
    void stop() { _stopped = true; }
    bool isRunning() const;
    const IMaintenanceJob &getJob() const { return *_job; }
    IMaintenanceJob &getJob() { return *_job; }
};

} // namespace proton

