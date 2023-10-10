// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "statusreport.h"
#include <vespa/vespalib/data/slime/inserter.h>

namespace proton {

/**
 * Utilities for converting state related objects to slime.
 */
struct StateReporterUtils
{
    static void convertToSlime(const StatusReport &statusReport,
                               const vespalib::slime::Inserter &inserter);
};

} // namespace proton

