// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generate_hash_table.h"

namespace vespalib {

GenerateHashTable::FoundBias GenerateHashTable::findBias(const KeyV& keys) const {
    for (KeyT bias = 1; bias != 0; bias++) {
        Taken taken = _takenSlots;
        bool allOk = true;
        for (KeyT key : keys) {
            Idx hash = mix_hash(key, bias, _size);
            if (taken[hash]) {
                allOk = false;
                break;
            }
            taken[hash] = true;
        }
        if (allOk) {
            return FoundBias{bias, taken};
        }
    }
    // this won't happen for reasonable input:
    throw "Could not find any possible bias";
}

void GenerateHashTable::findBiases() {
    std::vector<Bucket> buckets;
    for (Idx slot = 0; slot < _size; ++ slot) {
        buckets.emplace_back(Bucket{slot, {}});
        _bias[slot] = 0;
    }
    for (KeyT key : _keys) {
        Idx slot = mix_hash(key, 0, _size);
        buckets[slot].keys.push_back(key);
    }
    std::sort(buckets.begin(), buckets.end());
    for (auto & bucket : buckets) {
        if (bucket.keys.size() == 0) break;
        auto [bias, taken] = findBias(bucket.keys);
        _bias[bucket.slot] = bias;
        _takenSlots = taken;
    }
}

GenerateHashTable::~GenerateHashTable() = default;

}
