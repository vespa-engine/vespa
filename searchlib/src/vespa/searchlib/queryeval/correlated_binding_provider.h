// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "correlated_bindings.h"
#include <vespa/searchlib/fef/matchdata.h>
#include <limits>

namespace search::fef { class MatchDataLayout; }

namespace search::queryeval {

class CorrelateByBlueprint;
class CorrelateBySearch;

/** Caches requested projections for one document without materializing other variable combinations. */
class CorrelatedBindingProvider : public CorrelatedBindingReader {
    struct Projection {
        std::vector<uint32_t> columns;
        std::vector<BindingRow> rows;
        bool valid = false;
    };
    fef::MatchData::UP _match_data;
    std::unique_ptr<CorrelateBySearch> _search;
    uint32_t _docid_limit;
    uint32_t _docid = std::numeric_limits<uint32_t>::max();
    std::vector<Projection> _projections;
    size_t _visited_rows = 0;

public:
    CorrelatedBindingProvider(const CorrelateByBlueprint& bp, const fef::MatchDataLayout& layout, uint32_t docid_limit);
    ~CorrelatedBindingProvider() override;
    std::span<const BindingRow> read(uint32_t docid, std::span<const uint32_t> columns) override;
    /** Number of clause rows visited while computing uncached projections. */
    size_t visited_rows() const noexcept { return _visited_rows; }
};

} // namespace search::queryeval
