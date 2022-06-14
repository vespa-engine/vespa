// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "predicate_hash.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace search::predicate {

/**
 * Helper class for expanding ranges. This functionality is ported from
 * com.yahoo.vespa.indexinglanguage.predicate.ComplexNodeTransformer
 *
 * It is tested through document_features_store_test.cpp.
 */
class PredicateRangeExpander {
    static void debugLog(const char *format_str, const char *msg);

    template <typename InsertIt>
    static void addEdgePartition(const char *label, uint64_t value,
                                 bool negative, InsertIt out) {
        vespalib::string to_hash =
            vespalib::make_string("%s%s%" PRIu64, label,
                                  negative? "=-" : "=", value);
        debugLog("Hashing edge partition %s", to_hash.c_str());
        *out++ = PredicateHash::hash64(to_hash);
    }

    template <typename InsertIt>
    static void addPartitions(const char *label, uint64_t part,
                              uint64_t part_size, uint32_t first,
                              uint32_t last, bool negative, InsertIt out) {
        for (uint32_t i = first; i < last; ++i) {
            uint64_t from = (part + i) * part_size;
            uint64_t to = from + part_size - 1;
            if (negative) {
                std::swap(to, from);
            }
            vespalib::string to_hash =
                vespalib::make_string("%s%s%" PRIu64 "-%" PRIu64, label,
                                      negative? "=-" : "=", from, to);
            debugLog("Hashing partition %s", to_hash.c_str());
            *out++ = PredicateHash::hash64(to_hash);
        }
    }

    template <typename InsertIt>
    static void makePartitions(const char *label,
                               uint64_t from, uint64_t to,
                               uint64_t step_size, int32_t arity,
                               bool negative, InsertIt out) {
        uint32_t from_remainder = from % arity;
        uint32_t to_remainder = to % arity;
        uint64_t next_from = from - from_remainder;
        uint64_t next_to = to - to_remainder;
        if (next_from == next_to) {
            addPartitions(label, next_from, step_size,
                          from_remainder, to_remainder, negative, out);
        } else {
            if (from_remainder > 0) {
                addPartitions(label, next_from, step_size,
                              from_remainder, arity, negative, out);
                from = next_from + arity;
            }
            addPartitions(label, next_to, step_size,
                          0, to_remainder, negative, out);
            makePartitions(label, from / arity, to / arity,
                           step_size * arity, arity, negative, out);
        }
    }

    template <typename InsertIt>
    static void partitionRange(const char *label, uint64_t from, uint64_t to,
                               uint32_t arity, bool negative, InsertIt out) {
        uint32_t from_remainder = from % arity;
        // operate on exclusive upper bound.
        uint32_t to_remainder = (to + 1) % arity;
        uint64_t from_val = from - from_remainder;
        uint64_t to_val = to - to_remainder;
        if (from_val == to_val + 1) {
            addEdgePartition(label, from_val, negative, out);
            return;
        } else {
            if (from_remainder != 0) {
                addEdgePartition(label, from_val, negative, out);
                from_val += arity;
            }
            if (to_remainder != 0) {
                addEdgePartition(label, to_val + 1, negative, out);
            }
        }
        makePartitions(label, from_val / arity,
                       (to_val - (arity - 1)) / arity + 1,
                       arity, arity, negative, out);
    }

public:
    // Expands a range and returns the hash values through the insert iterator.
    template <typename InsertIt>
    static void expandRange(const char *label, int64_t from, int64_t to,
                            uint32_t arity, InsertIt out) {
        if (from < 0) {
            if (to < 0) {
                // Special case for to==-1. -X-0 means the same as -X-1,
                // but is more efficient.
                partitionRange(label, (to == -1 ? 0 : -to), uint64_t(0)-from, arity,
                               true, out);
            } else {
                partitionRange(label, 0, uint64_t(0)-from, arity, true, out);
                partitionRange(label, 0, to, arity, false, out);
            }
        } else {
            partitionRange(label, from, to, arity, false, out);
        }
    }
};

}
