// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::fakedata {

class FakePosting;

/**
 * Implementations of fake match loops used for testing and benchmarking.
 */
class FakeMatchLoop {
public:
    static int single_posting_scan(const FakePosting& posting, uint32_t doc_id_limit, uint64_t& elapsed_time_ns);
    static int single_posting_scan_with_unpack(const FakePosting& posting, uint32_t doc_id_limit, uint64_t& elapsed_time_ns);

    static int and_pair_posting_scan(const FakePosting& posting_1, const FakePosting& posting_2,
                                     uint32_t doc_id_limit, uint64_t& elapsed_time_ns);
    static int and_pair_posting_scan_with_unpack(const FakePosting& posting_1, const FakePosting& posting_2,
                                                 uint32_t doc_id_limit, uint64_t& elapsed_time_ns);
};

}
