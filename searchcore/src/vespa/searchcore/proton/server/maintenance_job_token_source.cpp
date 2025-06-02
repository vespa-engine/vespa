// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "maintenance_job_token_source.h"
#include "i_blockable_maintenance_job.h"
#include "maintenance_job_token.h"
#include <memory>

namespace proton {

namespace {

struct DeletedOrStoppedJob {
    bool operator()(const std::weak_ptr<IBlockableMaintenanceJob>& job) {
        auto j = job.lock();
        return (!j || j->stopped());
    }
};

}

MaintenanceJobTokenSource::MaintenanceJobTokenSource()
    : _mutex(),
      _jobs(),
      _token()
{
}

MaintenanceJobTokenSource::~MaintenanceJobTokenSource() = default;

void
MaintenanceJobTokenSource::remove_deleted_or_stopped_jobs()
{
    _jobs.erase(std::remove_if(_jobs.begin(), _jobs.end(), DeletedOrStoppedJob()), _jobs.end());
}

void
MaintenanceJobTokenSource::token_destroyed()
{
    std::unique_lock guard(_mutex);
    remove_deleted_or_stopped_jobs();
    auto existing_token = _token.lock();
    if (existing_token) {
        return; // get_token() was called after all references to old token were removed but before token_destroyed() was called.
    }
    while (!_jobs.empty()) {
        auto job = _jobs.front().lock();
        if (job && !job->stopped()) {
            _jobs.erase(_jobs.begin());
            auto token = std::make_shared<MaintenanceJobToken>(shared_from_this());
            _token = token;
            guard.unlock();
            job->got_token(token, false);
            return; // New token dispatched to a waiting job
        }
        remove_deleted_or_stopped_jobs();
    }
}

bool
MaintenanceJobTokenSource::get_token(std::shared_ptr<IBlockableMaintenanceJob> job)
{
    std::unique_lock guard(_mutex);
    auto existing_token = _token.lock();
    if (existing_token) {
        // Existing token is kept alive until return, thus it is not destroyed before this job is blocked.
        _jobs.emplace_back(job);
        guard.unlock();
        job->setBlocked(IBlockableMaintenanceJob::BlockedReason::JOB_TOKEN);
        return false;
    }
    auto token = std::make_shared<MaintenanceJobToken>(shared_from_this());
    _token = token;
    guard.unlock();
    job->got_token(token, true);
    return true;
}

}
