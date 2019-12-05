// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "maintenancecontroller.h"
#include "maintenancejobrunner.h"
#include "document_db_maintenance_config.h"
#include "i_blockable_maintenance_job.h"
#include <vespa/searchcorespi/index/i_thread_service.h>
#include <vespa/vespalib/util/closuretask.h>
#include <vespa/vespalib/util/scheduledexecutor.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.maintenancecontroller");

using document::BucketId;
using vespalib::Executor;
using vespalib::makeClosure;
using vespalib::makeTask;

namespace proton {

namespace {

class JobWrapperTask : public Executor::Task
{
private:
    MaintenanceJobRunner *_job;
public:
    JobWrapperTask(MaintenanceJobRunner *job) : _job(job) {}
    virtual void run() override { _job->run(); }
};

}

MaintenanceController::MaintenanceController(IThreadService &masterThread,
                                             vespalib::SyncableThreadExecutor & defaultExecutor,
                                             const DocTypeName &docTypeName)
    : IBucketFreezeListener(),
      _masterThread(masterThread),
      _defaultExecutor(defaultExecutor),
      _readySubDB(),
      _remSubDB(),
      _notReadySubDB(),
      _periodicTimer(),
      _config(),
      _frozenBuckets(masterThread),
      _started(false),
      _stopping(false),
      _docTypeName(docTypeName),
      _jobs(),
      _jobsLock()
{
    _frozenBuckets.addListener(this); // forward freeze/thaw to bmc
}

MaintenanceController::~MaintenanceController()
{
    kill();
    _frozenBuckets.removeListener(this);
}

void
MaintenanceController::registerJobInMasterThread(IMaintenanceJob::UP job)
{
    // Called by master write thread
    registerJob(_masterThread, std::move(job));
}

void
MaintenanceController::registerJobInDefaultPool(IMaintenanceJob::UP job)
{
    // Called by master write thread
    registerJob(_defaultExecutor, std::move(job));
}

void
MaintenanceController::registerJob(Executor & executor, IMaintenanceJob::UP job)
{
    // Called by master write thread
    Guard guard(_jobsLock);
    _jobs.push_back(std::make_shared<MaintenanceJobRunner>(executor, std::move(job)));
}


void
MaintenanceController::killJobs()
{
    // Called by master write thread during start/reconfig
    // Called by other thread during stop
    LOG(debug, "killJobs(): threadId=%zu", (size_t)FastOS_Thread::GetCurrentThreadId());
    _periodicTimer.reset();
    // No need to take _jobsLock as modification of _jobs also happens in master write thread.
    for (auto &job : _jobs) {
        job->stop(); // Make sure no more tasks are added to the executor
    }
    _defaultExecutor.sync();
    _defaultExecutor.sync();
    if (_masterThread.isCurrentThread()) {
        JobList tmpJobs = _jobs;
        {
            Guard guard(_jobsLock);
            _jobs.clear();
        }
        // Hold jobs until existing tasks have been drained
        _masterThread.execute(makeTask(makeClosure(this, &MaintenanceController::performHoldJobs, tmpJobs)));
    } else {
        // Wait for all tasks to be finished.
        // NOTE: We must sync 2 times as a task currently being executed can add a new
        // task to the executor as it might not see the new value of the stopped flag.
        _masterThread.sync();
        _masterThread.sync();
        // Clear jobs in master write thread, to avoid races
        _masterThread.execute(makeTask(makeClosure(this, &MaintenanceController::performClearJobs)));
        _masterThread.sync();
    }
}

void
MaintenanceController::performHoldJobs(JobList jobs)
{
    // Called by master write thread
    LOG(debug, "performHoldJobs(): threadId=%zu", (size_t)FastOS_Thread::GetCurrentThreadId());
    (void) jobs;
}

void
MaintenanceController::performClearJobs()
{
    // Called by master write thread
    LOG(debug, "performClearJobs(): threadId=%zu", (size_t)FastOS_Thread::GetCurrentThreadId());
    Guard guard(_jobsLock);
    _jobs.clear();
}


void
MaintenanceController::stop()
{
    assert(!_masterThread.isCurrentThread());
    _stopping = true;
    killJobs();
}

void
MaintenanceController::kill()
{
    stop();
    _readySubDB.clear();
    _remSubDB.clear();
    _notReadySubDB.clear();
}

void
MaintenanceController::start(const DocumentDBMaintenanceConfig::SP &config)
{
    // Called by master write thread
    assert(!_started);
    _config = config;
    _started = true;
    restart();
}


void
MaintenanceController::restart()
{
    // Called by master write thread
    if (!_started || _stopping || !_readySubDB.valid()) {
        return;
    }
    _periodicTimer = std::make_unique<vespalib::ScheduledExecutor>();

    addJobsToPeriodicTimer();
}

void
MaintenanceController::addJobsToPeriodicTimer()
{
    // No need to take _jobsLock as modification of _jobs also happens in master write thread.
    for (const auto &jw : _jobs) {
        const IMaintenanceJob &job = jw->getJob();
        LOG(debug, "addJobsToPeriodicTimer(): docType='%s', job.name='%s', job.delay=%f, job.interval=%f",
                _docTypeName.getName().c_str(), job.getName().c_str(), job.getDelay(), job.getInterval());
        if (job.getInterval() == 0.0) {
            jw->run();
            continue;
        }
        _periodicTimer->scheduleAtFixedRate(std::make_unique<JobWrapperTask>(jw.get()),
                                            job.getDelay(), job.getInterval());
    }
}

void
MaintenanceController::newConfig(const DocumentDBMaintenanceConfig::SP &config)
{
    // Called by master write thread
    _config = config;
    restart();
}

namespace {

void
assert_equal_meta_store_instances(const MaintenanceDocumentSubDB& old_db,
                                  const MaintenanceDocumentSubDB& new_db)
{
    if (old_db.valid() && new_db.valid()) {
        assert(old_db.meta_store().get() == new_db.meta_store().get());
    }
}

}

void
MaintenanceController::syncSubDBs(const MaintenanceDocumentSubDB &readySubDB,
                                  const MaintenanceDocumentSubDB &remSubDB,
                                  const MaintenanceDocumentSubDB &notReadySubDB)
{
    // Called by master write thread
    bool oldValid = _readySubDB.valid();
    assert(readySubDB.valid());
    assert(remSubDB.valid());
    // Document meta store instances should not change. Maintenance jobs depend on this fact.
    assert_equal_meta_store_instances(_readySubDB, readySubDB);
    assert_equal_meta_store_instances(_remSubDB, remSubDB);
    assert_equal_meta_store_instances(_notReadySubDB, notReadySubDB);
    _readySubDB = readySubDB;
    _remSubDB = remSubDB;
    _notReadySubDB = notReadySubDB;
    if (!oldValid && _started) {
        restart();
    }
}


void
MaintenanceController::notifyThawedBucket(const BucketId &bucket)
{
    (void) bucket;
    // No need to take _jobsLock as modification of _jobs also happens in master write thread.
    for (const auto &jw : _jobs) {
        IBlockableMaintenanceJob *job = jw->getJob().asBlockable();
        if (job && job->isBlocked()) {
            job->unBlock(IBlockableMaintenanceJob::BlockedReason::FROZEN_BUCKET);
        }
    }
}


} // namespace proton
