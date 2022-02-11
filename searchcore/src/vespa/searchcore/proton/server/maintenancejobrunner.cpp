// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "maintenancejobrunner.h"
#include <vespa/fastos/thread.h>
#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/lambdatask.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.maintenancejobrunner");

using vespalib::CpuUsage;
using vespalib::Executor;
using vespalib::makeLambdaTask;

namespace proton {


void
MaintenanceJobRunner::run()
{
    addExecutorTask();
}

void
MaintenanceJobRunner::stop() {
    {
        Guard guard(_lock);
        _stopped = true;
    }
    _job->stop();
}

void
MaintenanceJobRunner::addExecutorTask()
{
    Guard guard(_lock);
    if (!_stopped && !_job->isBlocked() && !_queued) {
        _queued = true;
        auto task = makeLambdaTask([this]() { runJobInExecutor(); });
        _executor.execute(CpuUsage::wrap(std::move(task), CpuUsage::Category::COMPACT));
    }
}

void
MaintenanceJobRunner::runJobInExecutor()
{
    {
        Guard guard(_lock);
        _queued = false;
        if (_stopped) {
            return;
        }
        _running = true;
    }
    bool finished = _job->run();
    if (LOG_WOULD_LOG(debug)) {
        FastOS_ThreadId threadId = FastOS_Thread::GetCurrentThreadId();
        LOG(debug,
            "runJobInExecutor(): job='%s', task='%p', threadId=%" PRIu64 "",
            _job->getName().c_str(), this, (uint64_t)threadId);
    }
    if (!finished) {
        addExecutorTask();
    }
    {
        Guard guard(_lock);
        _running = false;
    }
}

MaintenanceJobRunner::MaintenanceJobRunner(Executor &executor, IMaintenanceJob::UP job)
    : _executor(executor),
      _job(std::move(job)),
      _stopped(false),
      _queued(false),
      _running(false),
      _lock()
{
    _job->registerRunner(this);
}

bool
MaintenanceJobRunner::isRunnable() const
{
    Guard guard(_lock);
    return _running || _queued;
}

} // namespace proton
