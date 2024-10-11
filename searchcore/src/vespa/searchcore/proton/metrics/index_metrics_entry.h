// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "field_metrics_entry.h"

namespace proton {

/*
 * Class containing metrics for the index aspect of a field, i.e.
 * disk indexes and memory indexes.
 */
struct IndexMetricsEntry : public FieldMetricsEntry {
    IndexMetricsEntry(const std::string& field_name);
    ~IndexMetricsEntry() override;
};

} // namespace proton
