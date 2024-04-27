// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once
#include <vespa/searchcommon/attribute/basictype.h>
#include <vespa/searchcommon/attribute/collectiontype.h>
#include <cmath>
#include <cstddef>
#include "flow.h"

namespace search::queryeval::flow {

/**
 * The following constants and formulas were derived after benchmarking and analyzing
 * using the following benchmark program:
 * searchlib/src/tests/queryeval/iterator_benchmark
 *
 * The tests were executed on:
 *   - Machine with an Intel Xeon 2.5 GHz CPU with 48 cores and 256 Gb of memory.
 *   - Apple M1 MacBook Pro (2021) 32 GB.
 *
 * The benchmark summary shows the 'average ms per cost' of the different benchmark cases.
 * The constants and formulas are adjusted to balance 'average ms per cost' to be similar
 * across the different benchmark cases.
 *
 * The AND benchmark cases outputs the ratio (esti/forc) of the time_ms used by two query planning algorithms:
 * 'estimate' (legacy) and 'cost with allowed force strict' (new).
 * 'max_speedup' indicates the gain of using the new cost model, while 'min_speedup' indicates the loss.
 * The constants and formulas are also adjusted to maximize speedup, while reducing loss.
 * Tests used:
 *   - IteratorBenchmark::analyze_AND_filter_vs_IN
 *   - IteratorBenchmark::analyze_AND_filter_vs_OR
 *   - IteratorBenchmark::analyze_AND_filter_vs_IN_array
 *   - IteratorBenchmark::analyze_AND_bitvector_vs_IN
 */

/**
 * This function is used when calculating the strict cost of
 * intermediate and complex leaf blueprints that use a heap for their strict iterator implementation.
 * Tests used:
 *   - IteratorBenchmark::analyze_IN_strict
 *   - IteratorBenchmark::analyze_OR_strict
 */
inline double heap_cost(double my_est, size_t num_children) {
    return my_est * std::log2(std::max(size_t(1), num_children));
}

/**
 * Returns the number of memory indirections needed when doing lookups
 * in an attribute with the given type.
 */
inline size_t get_num_indirections(const attribute::BasicType& basic_type,
                                   const attribute::CollectionType& col_type) {
    size_t res = 0;
    if (basic_type == attribute::BasicType::STRING) {
        res += 1;
    }
    if (col_type != attribute::CollectionType::SINGLE) {
        res += 1;
    }
    return res;
}

// Non-strict cost of lookup based matching in an attribute (not fast-search).
// Test used: IteratorBenchmark::analyze_term_search_in_attributes_non_strict
inline double lookup_cost(size_t num_indirections) {
    return 1.0 + (num_indirections * 1.0);
}

// Non-strict cost of reverse lookup into a hash table (containing terms from a multi-term operator).
// Test used: IteratorBenchmark::analyze_IN_non_strict
inline double reverse_hash_lookup() {
    return 1.0;
}

// Strict cost of lookup based matching in an attribute (not fast-search).
// IteratorBenchmark::analyze_term_search_in_attributes_strict
inline double lookup_strict_cost(size_t num_indirections) {
    return lookup_cost(num_indirections);
}

/**
 * Estimates the cost of evaluating an always strict iterator (e.g. btree) in a non-strict context.
 *
 * When the estimate and strict cost is low, this models the cost of checking whether
 * the seek docid matches the docid the iterator is already positioned at.
 *
 * The resulting non-strict cost is most accurate when the inflow is 1.0.
 * The resulting non-strict cost is highly underestimated when the inflow goes to 0.0.
 * It is important to have a better estimate at higher inflows,
 * as the latency (time) penalty is higher if choosing wrong.
 */
inline double non_strict_cost_of_strict_iterator(double estimate, double strict_cost) {
    return strict_cost + strict_cost_diff(estimate, 1.0);
}

// Strict cost of matching in a btree posting list (e.g. fast-search attribute or memory index field).
// Test used: IteratorBenchmark::analyze_term_search_in_fast_search_attributes
inline double btree_strict_cost(double my_est) {
    return my_est;
}

// Non-strict cost of matching in a btree posting list (e.g. fast-search attribute or memory index field).
// Test used: IteratorBenchmark::analyze_btree_iterator_non_strict
inline double btree_cost(double my_est) {
    return non_strict_cost_of_strict_iterator(my_est, btree_strict_cost(my_est));
}

// Non-strict cost of matching in a bitvector.
inline double bitvector_cost() {
    return 1.0;
}

// Strict cost of matching in a bitvector.
// Test used: IteratorBenchmark::analyze_btree_vs_bitvector_iterators_strict
inline double bitvector_strict_cost(double my_est) {
    return 1.5 * my_est;
}

// Strict cost of matching in a disk index posting list.
// Test used: IteratorBenchmark::analyze_term_search_in_disk_index
inline double disk_index_strict_cost(double my_est) {
    return 1.5 * my_est;
}

// Non-strict cost of matching in a disk index posting list.
// Test used: IteratorBenchmark::analyze_term_search_in_disk_index
inline double disk_index_cost(double my_est) {
    return non_strict_cost_of_strict_iterator(my_est, disk_index_strict_cost(my_est));
}

}
