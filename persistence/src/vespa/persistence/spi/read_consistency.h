// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <iosfwd>
#include <cstdint>

namespace storage::spi {

enum class ReadConsistency : uint8_t {
    /**
     * A read operation with a strong consistency requirement requires that
     * any ACKed write operations must be visible to the operation.
     *
     * Formally, STRONG implies that read operations are linearizable with
     * regards to their corresponding writes.
     */
    STRONG,
    /**
     * A read operation with a weak consistency requirement implies that
     * visibility of recently ACKed operations is allowed to be on a best-
     * effort basis. This means it's possible to read stale data for operations
     * that have not yet been applied to the visible state.
     *
     * Formally, WEAK implies that read operations are NOT linearizable with
     * regards to their corresponding writes.
     */
    WEAK
};

std::ostream& operator<<(std::ostream&, ReadConsistency);

}

