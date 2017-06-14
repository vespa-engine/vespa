// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "content_proton_metrics.h"

namespace proton {

ContentProtonMetrics::ContentProtonMetrics()
    : metrics::MetricSet("content.proton", "", "Search engine metrics", nullptr),
      transactionLog(this),
      resourceUsage(this)
{
}

ContentProtonMetrics::~ContentProtonMetrics() {}

} // namespace proton
