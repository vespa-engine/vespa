// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "prepare_restart_costs_config.h"

namespace proton::flushengine {

PrepareRestartCostsConfig::PrepareRestartCostsConfig(double tls_replay_byte_cost_, double tls_replay_operation_cost_,
                                                     double flush_target_write_cost_, double flush_target_read_cost_)
    : tls_replay_byte_cost(tls_replay_byte_cost_),
      tls_replay_operation_cost(tls_replay_operation_cost_),
      flush_target_write_cost(flush_target_write_cost_),
      flush_target_read_cost(flush_target_read_cost_) {
}

} // namespace proton::flushengine
