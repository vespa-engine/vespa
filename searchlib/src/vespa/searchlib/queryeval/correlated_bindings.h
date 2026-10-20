// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "correlated_rows.h"
#include <vespa/searchlib/fef/objectstore.h>
#include <map>
#include <span>

namespace search::queryeval {

/** Reads query bindings with independent iterator state for one execution thread. */
class CorrelatedBindingReader {
public:
    virtual ~CorrelatedBindingReader() = default;
    /** Rows contain the requested columns in projection order. Other variables are existential. */
    virtual std::span<const BindingRow> read(uint32_t docid, std::span<const uint32_t> projection) = 0;
};

/** Query-owned factory shared by ranking and grouping. Readers must not outlive the query. */
class CorrelatedBindingFactory : public fef::Anything {
public:
    static std::string store_key(std::string_view label) { return "correlatedBindings." + std::string(label); }
    virtual const std::map<std::string, uint32_t>& columns() const = 0;
    /** Whether each column holds lowercased values, because the variable is compared without case. */
    virtual std::vector<bool> uncased_columns() const { return {}; }
    virtual std::unique_ptr<CorrelatedBindingReader> create() const = 0;
};

} // namespace search::queryeval
