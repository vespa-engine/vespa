// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "document_db_maintenance_config.h"
#include "i_maintenance_job.h"
#include "iwipeoldremovedfieldshandler.h"

namespace proton {

/**
 * Job that regularly wipes old removed fields from a document database.
 */
class WipeOldRemovedFieldsJob : public IMaintenanceJob
{
private:
    IWipeOldRemovedFieldsHandler &_handler;
    const double                  _ageLimitSeconds;

public:
    WipeOldRemovedFieldsJob(IWipeOldRemovedFieldsHandler &handler,
                            const DocumentDBWipeOldRemovedFieldsConfig &config);

    // Implements IMaintenanceJob
    virtual bool run() override;
};

} // namespace proton

