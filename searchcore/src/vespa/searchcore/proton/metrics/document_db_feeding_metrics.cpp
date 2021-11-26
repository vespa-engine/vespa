// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_db_feeding_metrics.h"

namespace proton {

DocumentDBFeedingMetrics::DocumentDBFeedingMetrics(metrics::MetricSet* parent)
    : MetricSet("feeding", {}, "feeding metrics in a document database", parent),
      commit(this)
{
}

DocumentDBFeedingMetrics::~DocumentDBFeedingMetrics() = default;

}
