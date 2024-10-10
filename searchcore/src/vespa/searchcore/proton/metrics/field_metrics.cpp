// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_metrics_entry.h"
#include "index_metrics_entry.h"
#include "field_metrics.hpp"

namespace proton {

template class FieldMetrics<AttributeMetricsEntry>;
template class FieldMetrics<IndexMetricsEntry>;

} // namespace proton
