// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/sortresults.h>

#include <memory>
#include <span>
#include <string>
#include <string_view>
#include <vector>

namespace proton::matching {

/**
 * Per-thread store of selected sort-feature values recorded during matching.
 * Consumption is a forward cursor over the ordered row view (identity order
 * when recording was monotonic, permutation order otherwise).
 * finalize_permutation() establishes docid order after non-monotonic recording.
 */
class SortFeatureStore : public INumericSortValueProvider {
public:
    SortFeatureStore(std::vector<std::string> public_names);
    ~SortFeatureStore() override = default;

    uint32_t num_features() const noexcept { return _num_features; }
    uint32_t num_rows() const noexcept { return _rows; }
    bool monotonic() const noexcept { return _monotonic; }

    void record(uint32_t docid, std::span<const double> values);
    void finalize_permutation();

    uint32_t ordinal(std::string_view public_name) const override;
    void seek(uint32_t docid) override;
    double get(uint32_t ordinal) const override;
    void consumed() override;

private:
    static constexpr uint32_t chunk_rows = 1024;

    struct Chunk {
        std::vector<uint32_t> docids;
        std::vector<double>   values; // row-major [local_row * num_features + ordinal]
        uint32_t              used = 0;
    };

    uint32_t ordered_row(uint32_t pos) const noexcept;
    uint32_t row_docid(uint32_t row) const;
    const double* row_values(uint32_t row) const;
    void clear_storage();

    uint32_t                            _num_features;
    uint32_t                            _rows;
    bool                                _monotonic;
    uint32_t                            _last_docid;
    uint32_t                            _seek_row;
    uint32_t                            _consume_pos;
    uint32_t                            _bound_docid;
    bool                                _bound;
    bool                                _consumed;
    std::vector<std::string>            _public_names;
    std::vector<std::unique_ptr<Chunk>> _chunks;
    std::vector<uint32_t>               _perm;
};

} // namespace proton::matching
