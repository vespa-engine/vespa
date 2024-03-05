// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once
#include <cmath>
#include <cstddef>

namespace search::queryeval::flow {

inline double heap_cost(double my_est, size_t num_children) {
    return my_est * std::log2(std::max(size_t(1),num_children));
}

inline double array_cost(double my_est, size_t num_children) {
    return my_est * num_children;
}

/**
 * The following constants and formulas were derived after analyzing term search over attributes
 * (with and without fast-search) and disk index by using the iterator benchmark program:
 * searchlib/src/tests/queryeval/iterator_benchmark
 *
 * The following tests were executed on a machine with an Intel Xeon 2.5 GHz CPU with 48 cores and 256 Gb of memory:
 * ./searchlib_iterator_benchmark_test_app --gtest_filter='*analyze_term_search*'
 *
 * The benchmark summary shows the 'average ms per cost' of the different benchmark cases.
 * The following constants and formulas were derived to balance 'average ms per cost' to be similar
 * across the different benchmark cases.
 */

// Non-strict cost of lookup based matching in an attribute (not fast-search).
inline double lookup_cost(size_t num_indirections) {
    return 1.0 + (num_indirections * 4.0);
}

// Strict cost of lookup based matching in an attribute (not fast-search).
inline double lookup_strict_cost(size_t num_indirections) {
    return lookup_cost(num_indirections);
}

// Non-strict cost of matching in a btree posting list (e.g. fast-search attribute or memory index field).
inline double btree_cost() {
    return 7.0;
}

// Strict cost of matching in a btree posting list (e.g. fast-search attribute or memory index field).
inline double btree_strict_cost(double my_est) {
    return my_est;
}

// Non-strict cost of matching in a disk index posting list.
inline double disk_index_cost() {
    return 12.0;
}

// Strict cost of matching in a disk index posting list.
inline double disk_index_strict_cost(double my_est) {
    return 1.5 * my_est;
}

}
