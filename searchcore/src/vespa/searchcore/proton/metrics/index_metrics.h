// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "index_metrics_entry.h"
#include "field_metrics.h"

namespace proton {

/*
 * Class containing metrics for the index aspect of multiple fields, i.e.
 * disk indexes and memory indexes.
 */
class IndexMetrics : public FieldMetrics<IndexMetricsEntry>
{
public:
    IndexMetrics(metrics::MetricSet* parent);
    ~IndexMetrics();
};

} // namespace proton
