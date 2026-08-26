// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sort_feature_store.h"

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <numeric>

namespace proton::matching {

namespace {

double sanitize(double value) {
    if (__builtin_expect(std::isnan(value) || std::isinf(value), false)) {
        return -HUGE_VAL;
    }
    return value;
}

} // namespace

SortFeatureStore::SortFeatureStore(std::vector<std::string> public_names)
    : _num_features(public_names.size()),
      _rows(0),
      _recorded_in_docid_order(true),
      _last_docid(0),
      _seek_row(~0u),
      _consume_pos(0),
      _bound_docid(0),
      _bound(false),
      _phase(Phase::recording),
      _public_names(std::move(public_names)),
      _chunks(),
      _read_order() {
}

void SortFeatureStore::record(uint32_t docid, std::span<const double> values) {
    if (_phase != Phase::recording || values.size() != _num_features) {
        abort();
    }
    if (_rows > 0 && docid < _last_docid) {
        _recorded_in_docid_order = false;
    }
    _last_docid = docid;
    if (_chunks.empty() || _chunks.back()->used == chunk_rows) {
        auto chunk = std::make_unique<Chunk>();
        chunk->docids.resize(chunk_rows);
        chunk->values.resize(static_cast<size_t>(chunk_rows) * _num_features);
        _chunks.push_back(std::move(chunk));
    }
    Chunk&   chunk = *_chunks.back();
    uint32_t local = chunk.used++;
    chunk.docids[local] = docid;
    double* dst = chunk.values.data() + static_cast<size_t>(local) * _num_features;
    for (uint32_t i = 0; i < _num_features; ++i) {
        dst[i] = sanitize(values[i]);
    }
    ++_rows;
}

void SortFeatureStore::ensure_sorted_for_read() {
    if (_phase != Phase::recording) {
        return;
    }
    _phase = Phase::reading;
    if (_recorded_in_docid_order || _rows == 0) {
        return;
    }
    _read_order.resize(_rows);
    std::iota(_read_order.begin(), _read_order.end(), 0u);
    std::sort(_read_order.begin(), _read_order.end(),
              [this](uint32_t a, uint32_t b) { return row_docid(a) < row_docid(b); });
}

uint32_t SortFeatureStore::ordinal(std::string_view public_name) const {
    for (uint32_t i = 0; i < _public_names.size(); ++i) {
        if (_public_names[i] == public_name) {
            return i;
        }
    }
    return invalid_ordinal;
}

uint32_t SortFeatureStore::ordered_row(uint32_t pos) const noexcept {
    return _read_order.empty() ? pos : _read_order[pos];
}

uint32_t SortFeatureStore::row_docid(uint32_t row) const {
    uint32_t chunk_idx = row / chunk_rows;
    uint32_t local = row % chunk_rows;
    return _chunks[chunk_idx]->docids[local];
}

const double* SortFeatureStore::row_values(uint32_t row) const {
    uint32_t chunk_idx = row / chunk_rows;
    uint32_t local = row % chunk_rows;
    return _chunks[chunk_idx]->values.data() + static_cast<size_t>(local) * _num_features;
}

void SortFeatureStore::seek(uint32_t docid) {
    if (_phase != Phase::reading) {
        abort();
    }
    if (_bound) {
        if (docid < _bound_docid) {
            abort();
        }
        if (docid == _bound_docid) {
            return;
        }
    }
    while (_consume_pos < _rows) {
        uint32_t row = ordered_row(_consume_pos);
        uint32_t stored = row_docid(row);
        if (stored >= docid) {
            if (stored != docid) {
                abort();
            }
            _seek_row = row;
            _bound_docid = docid;
            _bound = true;
            ++_consume_pos;
            return;
        }
        ++_consume_pos;
    }
    abort();
}

double SortFeatureStore::get(uint32_t ordinal) const {
    if (_seek_row == ~0u || ordinal >= _num_features) {
        abort();
    }
    return row_values(_seek_row)[ordinal];
}

void SortFeatureStore::consumed() {
    if (_phase == Phase::consumed) {
        return;
    }
    _phase = Phase::consumed;
    clear_storage();
}

void SortFeatureStore::clear_storage() {
    // vector::clear() keeps capacity. Swap-with-empty releases 4 bytes per
    // read-order index row (and the chunk buffers) before FastS radix scratch.
    std::vector<std::unique_ptr<Chunk>>().swap(_chunks);
    std::vector<uint32_t>().swap(_read_order);
    _rows = 0;
    _seek_row = ~0u;
    _consume_pos = 0;
    _bound = false;
}

} // namespace proton::matching
