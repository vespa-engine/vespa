// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "prepare_restart_costs.h"

#include "prepare_restart_costs_config.h"

#include <vespa/searchcorespi/flush/iflushtarget.h>

using search::SerialNum;
using searchcorespi::IFlushTarget;

namespace proton::flushengine {

PrepareRestartCosts::PrepareRestartCosts(const IFlushTarget& target, SerialNum current_serial,
                                         SerialNum flushed_serial, const PrepareRestartCostsConfig& cfg) noexcept
    : _replay_operation_cost(target.get_replay_operation_cost() * cfg.tls_replay_operation_cost),
      _replay_cost(_replay_operation_cost * (current_serial - flushed_serial)),
      _approx_bytes_to_write_to_disk(target.getApproxBytesToWriteToDisk()),
      _approx_bytes_to_read_from_disk(target.get_approx_bytes_to_read_from_disk()),
      _write_cost(_approx_bytes_to_write_to_disk * cfg.flush_target_write_cost),
      _read_cost(_approx_bytes_to_read_from_disk * cfg.flush_target_read_cost) {
}

} // namespace proton::flushengine
