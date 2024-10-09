// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_metrics.h"

namespace proton {

AttributeMetrics::AttributeMetrics(metrics::MetricSet* parent)
    : FieldMetrics(parent)
{
}

AttributeMetrics::~AttributeMetrics() = default;

} // namespace proton
