// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "maintenancedocumentsubdb.h"
#include "i_maintenance_job.h"
#include "frozenbuckets.h"
#include "ibucketfreezelistener.h"
#include <vespa/searchcore/proton/common/doctypename.h>
#include <mutex>
#include <vespa/vespalib/util/scheduledexecutor.h>

namespace vespalib {
    class Timer;
    class SyncableThreadExecutor;
    class Executor;
}
namespace searchcorespi::index { struct IThreadService; }

namespace proton {

class MaintenanceJobRunner;
class DocumentDBMaintenanceConfig;

/**
 * Class that controls the bucket moving between ready and notready sub databases
 * and a set of maintenance jobs for a document db.
 * The maintenance jobs are independent of the controller.
 */
class MaintenanceController : public IBucketFreezeListener
{
public:
    using IThreadService = searchcorespi::index::IThreadService;
    using DocumentDBMaintenanceConfigSP = std::shared_ptr<DocumentDBMaintenanceConfig>;
    using JobList = std::vector<std::shared_ptr<MaintenanceJobRunner>>;
    using UP = std::unique_ptr<MaintenanceController>;
    enum class State {INITIALIZING, STARTED, PAUSED, STOPPING};

    MaintenanceController(IThreadService &masterThread, vespalib::SyncableThreadExecutor & defaultExecutor, const DocTypeName &docTypeName);

    ~MaintenanceController() override;
    void registerJobInMasterThread(IMaintenanceJob::UP job);
    void registerJobInDefaultPool(IMaintenanceJob::UP job);

    void killJobs();

    JobList getJobList() const {
        Guard guard(_jobsLock);
        return _jobs;
    }

    void stop();
    void start(const DocumentDBMaintenanceConfigSP &config);
    void newConfig(const DocumentDBMaintenanceConfigSP &config);
    void updateMetrics(DocumentDBTaggedMetrics & metrics);

    void
    syncSubDBs(const MaintenanceDocumentSubDB &readySubDB,
               const MaintenanceDocumentSubDB &remSubdB,
               const MaintenanceDocumentSubDB &notReadySubDB);

    void kill();

    operator IBucketFreezer &() { return _frozenBuckets; }
    operator const IFrozenBucketHandler &() const { return _frozenBuckets; }
    operator IFrozenBucketHandler &() { return _frozenBuckets; }

    bool  getStarted() const { return _state >= State::STARTED; }
    bool getStopping() const { return _state == State::STOPPING; }
    bool getPaused() const { return _state == State::PAUSED; }

    const MaintenanceDocumentSubDB &    getReadySubDB() const { return _readySubDB; }
    const MaintenanceDocumentSubDB &      getRemSubDB() const { return _remSubDB; }
    const MaintenanceDocumentSubDB & getNotReadySubDB() const { return _notReadySubDB; }
    IThreadService & masterThread() { return _masterThread; }
private:
    using Mutex = std::mutex;
    using Guard = std::lock_guard<Mutex>;

    IThreadService                   &_masterThread;
    vespalib::SyncableThreadExecutor &_defaultExecutor;
    MaintenanceDocumentSubDB          _readySubDB;
    MaintenanceDocumentSubDB          _remSubDB;
    MaintenanceDocumentSubDB          _notReadySubDB;
    std::unique_ptr<vespalib::ScheduledExecutor>  _periodicTimer;
    DocumentDBMaintenanceConfigSP     _config;
    FrozenBuckets                     _frozenBuckets;
    State                             _state;
    const DocTypeName                &_docTypeName;
    JobList                           _jobs;
    mutable Mutex                     _jobsLock;

    void addJobsToPeriodicTimer();
    void restart();
    void notifyThawedBucket(const document::BucketId &bucket) override;
    void performHoldJobs(JobList jobs);
    void registerJob(vespalib::Executor & executor, IMaintenanceJob::UP job);
};

} // namespace proton
