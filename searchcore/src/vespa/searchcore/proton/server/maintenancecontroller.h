// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "maintenancedocumentsubdb.h"
#include "documentdbconfig.h"
#include "i_maintenance_job.h"
#include <vespa/searchcorespi/index/i_thread_service.h>
#include <vespa/vespalib/util/random.h>
#include <vespa/vespalib/util/timer.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcore/proton/persistenceengine/i_document_retriever.h>
#include "frozenbuckets.h"
#include "ibucketfreezelistener.h"
#include <mutex>

namespace proton
{

class MaintenanceJobRunner;


/**
 * Class that controls the bucket moving between ready and notready sub databases
 * and a set of maintenance jobs for a document db.
 * The maintenance jobs are independent of the controller.
 */
class MaintenanceController : public IBucketFreezeListener
{
public:

    typedef std::vector<std::shared_ptr<MaintenanceJobRunner>> JobList;

private:
    using Mutex = std::mutex;
    using Guard = std::lock_guard<Mutex>;

    searchcorespi::index::IThreadService &_masterThread;
    MaintenanceDocumentSubDB            _readySubDB;
    MaintenanceDocumentSubDB            _remSubDB;
    MaintenanceDocumentSubDB            _notReadySubDB;
    std::unique_ptr<vespalib::Timer>    _periodicTimer;
    DocumentDBMaintenanceConfig::SP     _config;
    FrozenBuckets                       _frozenBuckets;
    bool                                _started;
    bool                                _stopping;
    const DocTypeName                  &_docTypeName;
    JobList                             _jobs;
    mutable Mutex                       _jobsLock;

    void addJobsToPeriodicTimer();
    void restart(void);
    virtual void notifyThawedBucket(const document::BucketId &bucket) override;
    void performClearJobs();
    void performHoldJobs(JobList jobs);

public:
    typedef std::unique_ptr<MaintenanceController> UP;

    MaintenanceController(searchcorespi::index::IThreadService &masterThread,
                          const DocTypeName &docTypeName);

    virtual ~MaintenanceController(void);
    void registerJob(IMaintenanceJob::UP job);
    void killJobs();

    JobList getJobList() const {
        Guard guard(_jobsLock);
        return _jobs;
    }

    void stop(void);
    void start(const DocumentDBMaintenanceConfig::SP &config);
    void newConfig(const DocumentDBMaintenanceConfig::SP &config);

    void
    syncSubDBs(const MaintenanceDocumentSubDB &readySubDB,
               const MaintenanceDocumentSubDB &remSubdB,
               const MaintenanceDocumentSubDB &notReadySubDB);

    void kill();

    operator IBucketFreezer &() { return _frozenBuckets; }
    operator const IFrozenBucketHandler &() const { return _frozenBuckets; }
    operator IFrozenBucketHandler &() { return _frozenBuckets; }

    bool  getStarted() const { return _started; }
    bool getStopping() const { return _stopping; }

    const MaintenanceDocumentSubDB &    getReadySubDB() const { return _readySubDB; }
    const MaintenanceDocumentSubDB &      getRemSubDB() const { return _remSubDB; }
    const MaintenanceDocumentSubDB & getNotReadySubDB() const { return _notReadySubDB; }
};


} // namespace proton

