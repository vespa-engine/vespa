// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "flush_target_candidate.h"
#include "flushcontext.h"
#include "prepare_restart_costs_config.h"

#include <span>

namespace proton {

namespace flushengine {
class TlsStats;
}

/**
 * A set of flush targets that are candidates to be flushed.
 *
 * The total cost of using this set of candidates is:
 *   - the cost of replaying the TLS (after these are flushed) +
 *   - the cost of flushing these to disk
 */
class FlushTargetCandidates {
public:
    struct TlsReplayCost {
        double bytesCost;
        double operationsCost;
        TlsReplayCost(double bytesCost_, double operationsCost_) noexcept
            : bytesCost(bytesCost_), operationsCost(operationsCost_) {}
        [[nodiscard]] double totalCost() const noexcept { return bytesCost + operationsCost; }
    };

private:
    std::span<const FlushTargetCandidate> _candidates; // NOTE: ownership is handled outside
    size_t                                _num_candidates;
    TlsReplayCost                         _tlsReplayCost;
    double                                _flush_targets_replay_cost;
    double                                _flushTargetsWriteCost;
    double                                _flush_targets_read_cost;

public:
    using UP = std::unique_ptr<FlushTargetCandidates>;

    FlushTargetCandidates(std::span<const FlushTargetCandidate> candidates, size_t num_candidates,
                          const flushengine::TlsStats&                  tlsStats,
                          const flushengine::PrepareRestartCostsConfig& cfg) noexcept;

    [[nodiscard]] TlsReplayCost getTlsReplayCost() const noexcept { return _tlsReplayCost; }
    [[nodiscard]] double get_flush_targets_replay_cost() const noexcept { return _flush_targets_replay_cost; }
    [[nodiscard]] double getFlushTargetsWriteCost() const noexcept { return _flushTargetsWriteCost; }
    [[nodiscard]] double get_flush_targets_read_cost() const noexcept { return _flush_targets_read_cost; }
    [[nodiscard]] double getTotalCost() const noexcept {
        return getTlsReplayCost().totalCost() + getFlushTargetsWriteCost() + get_flush_targets_read_cost();
    }
    [[nodiscard]] FlushContext::List getCandidates() const;
};

} // namespace proton
