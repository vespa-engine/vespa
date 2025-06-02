// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <mutex>
#include <vector>

namespace proton {

class IBlockableMaintenanceJob;
class MaintenanceJobToken;

/*
 * Class generating a single maintenance job token at a time. A blockable maintenance job that
 * waits for a job token is registered in a queue. When the maintenance job token is destroyed, the
 * first job in the queue gets a new job token and is no longer blocked due to lack of a job token.
 */
class MaintenanceJobTokenSource : public std::enable_shared_from_this<MaintenanceJobTokenSource> {
    std::mutex _mutex;
    std::vector<std::weak_ptr<IBlockableMaintenanceJob>> _jobs;
    std::weak_ptr<MaintenanceJobToken> _token;
    void remove_deleted_or_stopped_jobs();
public:
    MaintenanceJobTokenSource();
    ~MaintenanceJobTokenSource();
    void token_destroyed();
    bool get_token(std::shared_ptr<IBlockableMaintenanceJob> job);
};

}
