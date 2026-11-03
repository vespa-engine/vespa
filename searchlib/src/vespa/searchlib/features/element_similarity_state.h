// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <algorithm>
#include <cstdint>

namespace search::features {

/*
 * This class stores state used to calculate similarity scores used by element similarity feature and text
 * similarity feature.
 */

struct ElementSimilarityState {
    uint32_t element_length;
    uint32_t matched_terms;
    int      sum_term_weight;
    uint32_t last_pos;
    double   sum_proximity_score;
    uint32_t last_idx;
    uint32_t num_in_order;

    double proximity;
    double order;
    double query_coverage;
    double field_coverage;
    double element_weight;

    ElementSimilarityState(uint32_t element_length_in, int32_t element_weight_in, uint32_t first_pos,
                           int32_t first_weight, uint32_t first_idx)
        : element_length(element_length_in),
          matched_terms(1),
          sum_term_weight(first_weight),
          last_pos(first_pos),
          sum_proximity_score(0.0),
          last_idx(first_idx),
          num_in_order(0),
          proximity(0.0),
          order(0.0),
          query_coverage(0.0),
          field_coverage(0.0),
          element_weight(element_weight_in) {}

    double proximity_score(uint32_t dist) {
        return (dist > 8) ? 0 : (1.0 - (((dist - 1) / 8.0) * ((dist - 1) / 8.0)));
    }

    bool want_match(uint32_t pos) { return (pos > last_pos); }

    void addMatch(uint32_t pos, int32_t weight, uint32_t idx) {
        sum_proximity_score += proximity_score(pos - last_pos);
        num_in_order += (idx > last_idx) ? 1 : 0;
        last_pos = pos;
        last_idx = idx;
        ++matched_terms;
        sum_term_weight += weight;
    }

    void calculate_scores(int total_term_weight) {
        element_length = std::max(element_length, matched_terms);
        double matches = matched_terms;
        if (matches < 2) {
            proximity = proximity_score(element_length);
            order = matches;
        } else {
            proximity = sum_proximity_score / (matches - 1);
            order = num_in_order / (double)(matches - 1);
        }
        query_coverage = sum_term_weight / (double)total_term_weight;
        field_coverage = matches / (double)element_length;
    }
};

} // namespace search::features
