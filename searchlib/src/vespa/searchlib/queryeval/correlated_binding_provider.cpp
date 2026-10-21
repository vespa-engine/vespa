// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "correlated_binding_provider.h"
#include "correlate_by_blueprint.h"
#include <vespa/searchlib/fef/matchdatalayout.h>

#include <algorithm>

namespace search::queryeval {

CorrelatedBindingProvider::CorrelatedBindingProvider(const CorrelateByBlueprint& bp,
                                                     const fef::MatchDataLayout& layout, uint32_t docid_limit)
    : _match_data(layout.createMatchData()), _search(bp.create_correlate_search(*_match_data)),
      _docid_limit(docid_limit), _projections() {}

CorrelatedBindingProvider::~CorrelatedBindingProvider() = default;

std::span<const BindingRow> CorrelatedBindingProvider::read(uint32_t docid, std::span<const uint32_t> columns) {
    if (!_search) return {};
    if (docid != _docid) {
        // Ranking and grouping may revisit an earlier document; only then rewind the iterators.
        if (_docid == std::numeric_limits<uint32_t>::max() || docid < _docid) {
            _search->initRange(docid, _docid_limit);
        }
        _docid = docid;
        for (auto& projection : _projections) projection.valid = false;
    }
    auto found = std::find_if(_projections.begin(), _projections.end(), [columns](const auto& projection) {
        return std::equal(projection.columns.begin(), projection.columns.end(), columns.begin(), columns.end());
    });
    if (found == _projections.end()) {
        _projections.push_back({std::vector<uint32_t>(columns.begin(), columns.end()), {}, false});
        found = std::prev(_projections.end());
    }
    if (!found->valid) {
        found->rows = _search->find_bindings(docid, columns);
        _visited_rows += _search->visited_rows();
        found->valid = true;
    }
    return found->rows;
}

} // namespace search::queryeval
