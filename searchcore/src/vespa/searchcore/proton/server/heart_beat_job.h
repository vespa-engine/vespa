// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "document_db_maintenance_config.h"
#include "i_maintenance_job.h"
#include "iheartbeathandler.h"

namespace proton {

/**
 * Job that regularly does heart beating on a given handler.
 *
 * The FeedHandler is typically acting as a handler to do
 * heart beating on its underlying components.
 */
class HeartBeatJob : public IMaintenanceJob
{
private:
    IHeartBeatHandler &_handler;

public:
    HeartBeatJob(IHeartBeatHandler &handler,
                 const DocumentDBHeartBeatConfig &config);

    // Implements IMaintenanceJob
    bool run() override;
    void onStop() override { }
};

} // namespace proton

