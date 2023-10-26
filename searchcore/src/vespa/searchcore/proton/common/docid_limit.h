// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <atomic>

namespace proton {

/**
 * Class representing the end of a local document id range.
 */
class DocIdLimit
{
private:
    std::atomic<uint32_t> _docIdLimit;

public:
    explicit DocIdLimit(uint32_t docIdLimit) : _docIdLimit(docIdLimit) {}
    void set(uint32_t docIdLimit) { _docIdLimit = docIdLimit; }
    uint32_t get() const { return _docIdLimit; }

    void bumpUpLimit(uint32_t newLimit) {
        for (;;) {
            uint32_t oldLimit = _docIdLimit;
            if (newLimit <= oldLimit)
                break;
            if (_docIdLimit.compare_exchange_weak(oldLimit, newLimit,
                                                  std::memory_order_release,
                                                  std::memory_order_relaxed))
                break;
        }
    }
};

} // namespace proton

