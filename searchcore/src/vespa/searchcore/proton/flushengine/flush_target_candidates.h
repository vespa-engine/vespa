// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "flush_target_candidate.h"
#include "prepare_restart_flush_strategy.h"
#include <span>

namespace proton {

namespace flushengine { class TlsStats; }

/**
 * A set of flush targets that are candidates to be flushed.
 *
 * The total cost of using this set of candidates is:
 *   - the cost of replaying the TLS (after these are flushed) +
 *   - the cost of flushing these to disk
 */
class FlushTargetCandidates
{
public:
    struct TlsReplayCost {
        double bytesCost;
        double operationsCost;
        TlsReplayCost(double bytesCost_, double operationsCost_)
            : bytesCost(bytesCost_),
              operationsCost(operationsCost_)
        {}
        double totalCost() const { return bytesCost + operationsCost; }
    };
private:
    std::span<const FlushTargetCandidate> _candidates; // NOTE: ownership is handled outside
    size_t  _num_candidates;
    TlsReplayCost _tlsReplayCost;
    double _flushTargetsWriteCost;
    double _flush_targets_read_cost;

    using Config = PrepareRestartFlushStrategy::Config;

public:
    using UP = std::unique_ptr<FlushTargetCandidates>;

    FlushTargetCandidates(std::span<const FlushTargetCandidate> candidates,
                          size_t num_candidates,
                          const flushengine::TlsStats &tlsStats,
                          const Config &cfg);

    TlsReplayCost getTlsReplayCost() const { return _tlsReplayCost; }
    double getFlushTargetsWriteCost() const { return _flushTargetsWriteCost; }
    double get_flush_targets_read_cost() const noexcept { return _flush_targets_read_cost; }
    double getTotalCost() const { return getTlsReplayCost().totalCost() + getFlushTargetsWriteCost() +
        get_flush_targets_read_cost(); }
    FlushContext::List getCandidates() const;
};

} // namespace proton
