// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_maintenance_job.h"
#include <vespa/fastos/timestamp.h>

namespace proton {

class ICommitable;

/**
 * Job that regularly commits the documentdb.
 */
class DocumentDBCommitJob : public IMaintenanceJob
{
private:
    ICommitable & _committer;

public:
    DocumentDBCommitJob(ICommitable & committer, fastos::TimeStamp visibilityDelay);

    bool run() override;
};

} // namespace proton

