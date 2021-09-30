// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>

namespace proton {

/*
 * Flush config, used to adjust flush targets as needed.
 */
class DocumentDBFlushConfig {
    uint32_t _maxFlushed;
    uint32_t _maxFlushedRetired;

public:
    DocumentDBFlushConfig() noexcept;
    DocumentDBFlushConfig(uint32_t maxFlushed, uint32_t maxFlushedRetired) noexcept;
    bool operator==(const DocumentDBFlushConfig &rhs) const noexcept;
    uint32_t getMaxFlushed() const noexcept { return _maxFlushed; }
    uint32_t getMaxFlushedRetired() const noexcept { return _maxFlushedRetired; }
};

} // namespace proton
