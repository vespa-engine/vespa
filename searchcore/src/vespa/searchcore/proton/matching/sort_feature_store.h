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
 *
 * Lifetime:
 * - construct with X public names;
 * - record(docid, X doubles); docids may arrive in any order;
 * - ensure_sorted_for_read() ends recording; no record() afterward;
 * - seek(docid) in non-decreasing docid order, then get(ordinal) for the
 *   bound row;
 * - consumed() releases storage.
 *
 * Unrequested rows between seeks are skipped.
 * ordinal(name) is the 0..X-1 index used with get().
 */
class SortFeatureStore : public INumericSortValueProvider {
public:
    SortFeatureStore(std::vector<std::string> public_names);
    ~SortFeatureStore() override;

    uint32_t num_features() const noexcept { return _num_features; }
    uint32_t num_rows() const noexcept { return _rows; }

    void record(uint32_t docid, std::span<const double> values);
    void ensure_sorted_for_read();

    uint32_t ordinal(std::string_view public_name) const override;
    void seek(uint32_t docid) override;
    double get(uint32_t ordinal) const override;
    bool failed() const noexcept override { return _phase == Phase::failed; }
    void consumed() override;

private:
    enum class Phase { recording, reading, consumed, failed };

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
    void fail(const char* reason, uint32_t docid);
    const char* current_phase() const noexcept;

    uint32_t                            _num_features;
    uint32_t                            _rows;
    bool                                _recorded_in_docid_order;
    uint32_t                            _last_docid;
    uint32_t                            _seek_row;
    uint32_t                            _consume_pos;
    uint32_t                            _bound_docid;
    bool                                _bound;
    Phase                               _phase;
    std::vector<std::string>            _public_names;
    std::vector<std::unique_ptr<Chunk>> _chunks;
    std::vector<uint32_t>               _read_order;
};

} // namespace proton::matching
