// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_maintenance_job.h"
#include <vespa/vespalib/util/time.h>

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
    DocumentDBCommitJob(ICommitable & committer, vespalib::duration visibilityDelay);

    bool run() override;
};

} // namespace proton

