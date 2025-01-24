// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_maintenance_job.h"
#include <vespa/vespalib/util/time.h>

namespace proton {

struct IAttributeManager;

/**
 * Job that regularly clears the search cache for imported attributes.
 */
class ClearImportedAttributeSearchCacheJob : public IMaintenanceJob
{
    std::shared_ptr<IAttributeManager> _mgr;
public:
    ClearImportedAttributeSearchCacheJob(std::shared_ptr<IAttributeManager> mgr, vespalib::duration visibilityDelay);
    bool run() override;
    void onStop() override;
};

}
