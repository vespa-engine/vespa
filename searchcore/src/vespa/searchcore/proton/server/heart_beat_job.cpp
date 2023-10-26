// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "heart_beat_job.h"

namespace proton {

HeartBeatJob::HeartBeatJob(IHeartBeatHandler &handler,
                           const DocumentDBHeartBeatConfig &config)
    : IMaintenanceJob("heart_beat", config.getInterval(), config.getInterval()),
      _handler(handler)
{
}

bool
HeartBeatJob::run()
{
    _handler.heartBeat();
    return true;
}

} // namespace proton
