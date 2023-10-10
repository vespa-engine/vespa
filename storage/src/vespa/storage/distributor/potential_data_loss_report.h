// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>

namespace storage::distributor {

/**
 * Represents the amount of data a distributor reasons _may_ have become unavailable
 * due to all bucket replicas no longer being present.
 */
struct PotentialDataLossReport {
    size_t buckets   = 0;
    size_t documents = 0;

    constexpr PotentialDataLossReport() noexcept = default;

    constexpr PotentialDataLossReport(size_t buckets_, size_t documents_) noexcept
        : buckets(buckets_),
          documents(documents_)
    {}

    void merge(const PotentialDataLossReport& other) noexcept {
        buckets += other.buckets;
        documents += other.documents;
    }
};

}
