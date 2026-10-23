// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton::flushengine {

/*
 * This config is used when calculating prepare restart costs for a flush target.
 */
struct PrepareRestartCostsConfig {
    double tls_replay_byte_cost;
    double tls_replay_operation_cost;
    double flush_target_write_cost;
    double flush_target_read_cost;
    PrepareRestartCostsConfig(double tls_replay_byte_cost_, double tls_replay_operation_cost_,
                              double flush_target_write_cost_, double flush_target_read_cost_);
};

} // namespace proton::flushengine
