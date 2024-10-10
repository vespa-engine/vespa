// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "index_metrics.h"

namespace proton {

IndexMetrics::IndexMetrics(metrics::MetricSet* parent)
    : FieldMetrics(parent)
{
}

IndexMetrics::~IndexMetrics() = default;

} // namespace proton
