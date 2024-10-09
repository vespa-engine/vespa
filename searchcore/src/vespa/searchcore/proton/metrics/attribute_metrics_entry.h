// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "field_metrics_entry.h"

namespace proton {

/*
 * Class containing metrics for the attribute aspect of a field, i.e.
 * an attribute vector.
 */
struct AttributeMetricsEntry : public FieldMetricsEntry {
    AttributeMetricsEntry(const std::string& field_name);
    ~AttributeMetricsEntry() override;
};

} // namespace proton
