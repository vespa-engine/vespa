// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metrics.h>
#include "resource_usage_metrics.h"
#include "trans_log_server_metrics.h"

namespace proton {

/**
 * Metric set for all metrics reported by proton.
 *
 * This class uses the new metric naming scheme decided in architect meeting 2014-10-30.
 * All proton metrics use the prefix "content.proton." and dimensions where appropriate.
 * For instance, all document db metrics use the dimension "documenttype":"mydoctype"
 * instead of using the document type name as part of metric names.
 */
struct ContentProtonMetrics : metrics::MetricSet
{
    TransLogServerMetrics transactionLog;
    ResourceUsageMetrics resourceUsage;

    ContentProtonMetrics();
    ~ContentProtonMetrics();

};

} // namespace proton
