// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>

namespace proton::flushengine {

/*
 * Class representing statistics for a transaction log server domain used to
 * adjust flush strategy.
 */
class TlsStats {
    uint64_t _numBytes;
    uint64_t _firstSerial;
    uint64_t _lastSerial;

public:
    TlsStats() noexcept : _numBytes(0), _firstSerial(0), _lastSerial(0) {}
    TlsStats(uint64_t numBytes, uint64_t firstSerial, uint64_t lastSerial) noexcept
        : _numBytes(numBytes), _firstSerial(firstSerial), _lastSerial(lastSerial) {}

    [[nodiscard]] bool operator==(const TlsStats& b) const noexcept {
        return (_numBytes == b._numBytes) && (_firstSerial == b._firstSerial) && (_lastSerial == b._lastSerial);
    }

    [[nodiscard]] uint64_t getNumBytes() const noexcept { return _numBytes; }
    [[nodiscard]] uint64_t getFirstSerial() const noexcept { return _firstSerial; }
    [[nodiscard]] uint64_t getLastSerial() const noexcept { return _lastSerial; }
};

} // namespace proton::flushengine
