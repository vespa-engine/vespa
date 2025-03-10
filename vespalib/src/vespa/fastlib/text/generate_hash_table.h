// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <algorithm>
#include <cstdint>
#include "mix_hash.h"

namespace vespalib {

/**
 * Class for generating a perfect hash table.  Given a set of all
 * possible valid keys, creates a hash object which maps each of
 * those to a unique index in range [0, #keys>
 * Any other key will also map to some random index in the range,
 * so you need to check that the result of using the index
 * actually matches the key.
 */
class GenerateHashTable {
    using Idx = uint32_t;
    using KeyT = uint32_t;
    using KeyV = std::vector<KeyT>;
    using Taken = std::vector<bool>;
    struct Bucket {
        Idx slot;
        std::vector<KeyT> keys;
        bool operator< (const Bucket& other) {
            return keys.size() > other.keys.size();
        }
    };
    const KeyV _keys;
    KeyV _bias;
    const KeyT _size;
    Taken _takenSlots;

    GenerateHashTable(KeyV keys)
      : _keys(std::move(keys)),
        _bias(),
        _size(_keys.size())
    {
        _bias.resize(_size);
        _takenSlots.resize(_size);
    }

    struct FoundBias { KeyT bias; Taken taken; };

    FoundBias findBias(const KeyV& keys) const {
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

    void findBiases() {
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

public:

    /** Hashes each valid value of "key" to a unique index */
    struct BiasedHash {
        const KeyV biasTable;
        Idx hash(KeyT key) const {
            Idx l1hash = mix_hash(key, 0, biasTable.size());
            KeyT bias = biasTable[l1hash];
            return mix_hash(key, bias, biasTable.size());
        }
    };

    /** create bias table for the given set of keys */
    static BiasedHash generate_bias(KeyV keys) {
        GenerateHashTable generator(std::move(keys));
        generator.findBiases();
        BiasedHash result(std::move(generator._bias));
        return result;
    }

};

} // namespace vespalib
