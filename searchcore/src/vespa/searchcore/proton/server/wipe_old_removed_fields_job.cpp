// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include "wipe_old_removed_fields_job.h"
#include <vespa/fastos/timestamp.h>

using fastos::ClockSystem;
using fastos::TimeStamp;

namespace proton {

WipeOldRemovedFieldsJob::WipeOldRemovedFieldsJob(IWipeOldRemovedFieldsHandler &handler,
                                                 const DocumentDBWipeOldRemovedFieldsConfig &config)
    : IMaintenanceJob("wipe_old_removed_fields", config.getInterval(), config.getInterval()),
      _handler(handler),
      _ageLimitSeconds(config.getAge())
{
}

bool
WipeOldRemovedFieldsJob::run()
{
    TimeStamp wipeTimeLimit = TimeStamp(ClockSystem::now()) -
        TimeStamp(int64_t(_ageLimitSeconds) * TimeStamp::SEC);
    _handler.wipeOldRemovedFields(wipeTimeLimit);
    return true;
}

} // namespace proton
