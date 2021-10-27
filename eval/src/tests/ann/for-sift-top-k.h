// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

struct TopK {
    static constexpr size_t K = 100;
    Hit hits[K];

    size_t recall(const TopK &other) const {
        size_t overlap = 0;
        size_t i = 0;
        size_t j = 0;
        while (i < K && j < K) {
            if (hits[i].docid == other.hits[j].docid) {
                ++overlap;
                ++i;
                ++j;
            } else if (hits[i].distance < other.hits[j].distance) {
                ++i;
            } else {
                ++j;
            }
        }
        return overlap;
    }
};
