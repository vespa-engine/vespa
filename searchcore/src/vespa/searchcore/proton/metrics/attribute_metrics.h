// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_metrics_entry.h"
#include "field_metrics.h"

namespace proton {

/*
 * Class containing metrics for the attribute aspect of multiple fields, i.e.
 * attribute vectors.
 */
class AttributeMetrics : public FieldMetrics<AttributeMetricsEntry>
{
public:
    AttributeMetrics(metrics::MetricSet* parent);
    ~AttributeMetrics();
};

} // namespace proton
