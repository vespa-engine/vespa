// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "compression.h"

namespace search::bitcompression {

/*
 * Class collecting raw features data for a (word, document tuple), used by
 * EG2PosOccDecodeContext::readFeatures and EG2PosOccDecodeContext::readFeatures.
 * Disk index fusion uses raw features when feature parameters are identical to
 * improve fusion speed, cf. FieldMerger::select_cooked_or_raw_features.
 */
class RawFeaturesCollector {
    uint64_t                         _start_offset;
    const uint64_t*                  _raw_features;

    void collect(search::index::DocIdAndFeatures& features, const uint64_t* compr) {
        auto& blob = features.blob();
        auto* raw_features = _raw_features;
        while (raw_features < compr) {
            blob.emplace_back(*raw_features);
            ++raw_features;
        }
    }

public:
    RawFeaturesCollector(const DecodeContext64Base& dc, search::index::DocIdAndFeatures& features)
        : _start_offset(dc.getReadOffset()),
          _raw_features(dc.getCompr())
    {
        features.clear_features(dc.getBitOffset());
        features.set_has_raw_data(true);
    }

    void collect_before_read_compr_buffer(const DecodeContext64Base& dc, search::index::DocIdAndFeatures& features) {
        collect(features, dc._valI);
    }

    void fixup_after_read_compr_buffer(const DecodeContext64Base& dc) {
        _raw_features = dc._valI;
    }

    void finish(const DecodeContext64Base& dc, search::index::DocIdAndFeatures& features) {
        collect(features, dc._valI);
        auto end_offset = dc.getReadOffset();
        features.set_bit_length( end_offset - _start_offset);
    }
};

}
