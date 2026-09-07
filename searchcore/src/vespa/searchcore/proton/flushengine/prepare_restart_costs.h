// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/serialnum.h>

namespace searchcorespi {
class IFlushTarget;
}

namespace proton::flushengine {

struct PrepareRestartCostsConfig;

/**
 * Class describing the flush and replay costs for a flush target.
 */
class PrepareRestartCosts {
    double   _replay_operation_cost;
    double   _replay_cost;
    uint64_t _approx_bytes_to_write_to_disk;
    uint64_t _approx_bytes_to_read_from_disk;
    double   _write_cost;
    double   _read_cost;

public:
    PrepareRestartCosts(const searchcorespi::IFlushTarget& target, search::SerialNum current_serial,
                        search::SerialNum flushed_serial, const PrepareRestartCostsConfig& cfg) noexcept;
    [[nodiscard]] double replay_cost() const noexcept { return _replay_cost; }
    [[nodiscard]] double write_cost() const noexcept { return _write_cost; }
    [[nodiscard]] double read_cost() const noexcept { return _read_cost; }
};

} // namespace proton::flushengine
