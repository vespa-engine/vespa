// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "maintenancecontroller.h"
#include "maintenancejobrunner.h"
#include "document_db_maintenance_config.h"
#include "i_blockable_maintenance_job.h"
#include <vespa/searchcorespi/index/i_thread_service.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/scheduledexecutor.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.maintenancecontroller");

using document::BucketId;
using vespalib::Executor;
using vespalib::makeLambdaTask;

namespace proton {

namespace {

class JobWrapperTask : public Executor::Task
{
private:
    MaintenanceJobRunner *_job;
public:
    JobWrapperTask(MaintenanceJobRunner *job) : _job(job) {}
    void run() override { _job->run(); }
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
      _state(State::INITIALIZING),
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
    if (_state == State::STARTED) {
        _state = State::PAUSED;
    }
    // Called by master write thread
    assert(_masterThread.isCurrentThread());
    LOG(debug, "killJobs(): threadId=%zu", (size_t)FastOS_Thread::GetCurrentThreadId());
    _periodicTimer.reset();
    // No need to take _jobsLock as modification of _jobs also happens in master write thread.
    for (auto &job : _jobs) {
        job->stop(); // Make sure no more tasks are added to the executor
    }
    _defaultExecutor.sync();
    _defaultExecutor.sync();
    JobList tmpJobs = _jobs;
    {
        Guard guard(_jobsLock);
        _jobs.clear();
    }
    // Hold jobs until existing tasks have been drained
    _masterThread.execute(makeLambdaTask([this, jobs=std::move(tmpJobs)]() {
        performHoldJobs(std::move(jobs));
    }));
}

void
MaintenanceController::updateMetrics(DocumentDBTaggedMetrics & metrics)
{
    Guard guard(_jobsLock);
    for (auto &job : _jobs) {
        job->getJob().updateMetrics(metrics); // Make sure no more tasks are added to the executor
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
MaintenanceController::stop()
{
    assert(!_masterThread.isCurrentThread());
    _masterThread.execute(makeLambdaTask([this]() { _state = State::STOPPING; killJobs(); }));
    _masterThread.sync();  // Wait for killJobs()
    _masterThread.sync();  // Wait for already scheduled maintenance jobs and performHoldJobs
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
    assert(_state == State::INITIALIZING);
    _config = config;
    _state = State::STARTED;
    restart();
}


void
MaintenanceController::restart()
{
    // Called by master write thread
    if (!getStarted() || getStopping() || !_readySubDB.valid()) {
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
                _docTypeName.getName().c_str(), job.getName().c_str(), vespalib::to_s(job.getDelay()), vespalib::to_s(job.getInterval()));
        if (job.getInterval() == vespalib::duration::zero()) {
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
    if (!oldValid && getStarted()) {
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
