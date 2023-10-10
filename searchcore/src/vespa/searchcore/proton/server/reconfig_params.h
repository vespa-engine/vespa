// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentdbconfig.h"

namespace proton {

/**
 * Class specifying which components that should change after a reconfig.
 */
class ReconfigParams {
private:
    const DocumentDBConfig::ComparisonResult _res;

public:
    ReconfigParams(const DocumentDBConfig::ComparisonResult &res);
    bool configHasChanged() const;
    bool shouldSchemaChange() const;
    bool shouldMatchersChange() const;
    bool shouldIndexManagerChange() const;
    bool shouldAttributeManagerChange() const;
    bool shouldSummaryManagerChange() const;
    bool shouldSubDbsChange() const;
    bool shouldMaintenanceControllerChange() const;
    bool shouldAttributeWriterChange() const;
};

}
