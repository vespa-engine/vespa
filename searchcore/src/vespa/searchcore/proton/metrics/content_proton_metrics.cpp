// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "content_proton_metrics.h"

namespace proton {

ContentProtonMetrics::ProtonExecutorMetrics::ProtonExecutorMetrics(metrics::MetricSet *parent)
    : metrics::MetricSet("executor", {}, "Metrics for top-level executors shared among all document databases", parent),
      proton("proton", this),
      flush("flush", this),
      match("match", this),
      docsum("docsum", this),
      shared("shared", this),
      warmup("warmup", this),
      field_writer("field_writer", this)
{
}

ContentProtonMetrics::ProtonExecutorMetrics::~ProtonExecutorMetrics() = default;

ContentProtonMetrics::ContentProtonMetrics()
    : metrics::MetricSet("content.proton", {}, "Search engine metrics", nullptr),
      transactionLog(this),
      resourceUsage(this),
      executor(this)
{
}

ContentProtonMetrics::~ContentProtonMetrics() = default;

}
