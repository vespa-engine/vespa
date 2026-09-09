// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_target_candidates.h"

#include "tls_stats.h"

using proton::flushengine::PrepareRestartCostsConfig;
using search::SerialNum;

namespace proton {

using TlsReplayCost = FlushTargetCandidates::TlsReplayCost;

namespace {

[[nodiscard]] SerialNum calculateReplayStartSerial(std::span<const FlushTargetCandidate> candidates,
                                                   size_t num_candidates, const flushengine::TlsStats& tlsStats) {
    if (num_candidates == 0) {
        return tlsStats.getFirstSerial();
    }
    if (num_candidates == candidates.size()) {
        return tlsStats.getLastSerial() + 1;
    }
    return candidates[num_candidates].get_flushed_serial() + 1;
}

[[nodiscard]] TlsReplayCost calculateTlsReplayCost(const flushengine::TlsStats&     tlsStats,
                                                   const PrepareRestartCostsConfig& cfg,
                                                   SerialNum                        replayStartSerial) noexcept {
    SerialNum replayEndSerial = tlsStats.getLastSerial();
    SerialNum numTotalOperations = replayEndSerial - tlsStats.getFirstSerial() + 1;
    if (numTotalOperations == 0) {
        return TlsReplayCost(0.0, 0.0);
    }
    double    numBytesPerOperation = (double)tlsStats.getNumBytes() / (double)numTotalOperations;
    SerialNum numOperationsToReplay = replayEndSerial + 1 - replayStartSerial;
    double    numBytesToReplay = numBytesPerOperation * numOperationsToReplay;
    return TlsReplayCost((numBytesToReplay * cfg.tls_replay_byte_cost),
                         (numOperationsToReplay * cfg.tls_replay_operation_cost));
}

[[nodiscard]] double calculateFlushTargetsWriteCost(std::span<const FlushTargetCandidate> candidates,
                                                    size_t                                num_candidates) noexcept {
    double result = 0;
    for (size_t i = 0; i < candidates.size(); ++i) {
        if (i < num_candidates || candidates[i].get_always_flush()) {
            result += candidates[i].get_write_cost();
        }
    }
    return result;
}

[[nodiscard]] double calculate_flush_targets_read_cost(std::span<const FlushTargetCandidate> candidates,
                                                       size_t num_candidates) noexcept {
    double result = 0;
    for (size_t i = 0; i < num_candidates; ++i) {
        if (i < num_candidates || candidates[i].get_always_flush()) {
            result += candidates[i].get_read_cost();
        }
    }
    return result;
}

[[nodiscard]] double calculate_flush_targets_replay_cost(std::span<const FlushTargetCandidate> candidates,
                                                         size_t num_candidates) noexcept {
    double result = 0;
    for (size_t i = 0; i < num_candidates; ++i) {
        if (i >= num_candidates && !candidates[i].get_always_flush()) {
            result += candidates[i].replay_cost();
        }
    }
    return result;
}

} // namespace

FlushTargetCandidates::FlushTargetCandidates(std::span<const FlushTargetCandidate> candidates, size_t num_candidates,
                                             const flushengine::TlsStats&     tlsStats,
                                             const PrepareRestartCostsConfig& cfg) noexcept
    : _candidates(candidates),
      _num_candidates(std::min(num_candidates, _candidates.size())),
      _tlsReplayCost(
          calculateTlsReplayCost(tlsStats, cfg, calculateReplayStartSerial(_candidates, _num_candidates, tlsStats))),
      _flush_targets_replay_cost(calculate_flush_targets_replay_cost(_candidates, _num_candidates)),
      _flushTargetsWriteCost(calculateFlushTargetsWriteCost(_candidates, _num_candidates)),
      _flush_targets_read_cost(calculate_flush_targets_read_cost(_candidates, num_candidates)) {
}

FlushContext::List FlushTargetCandidates::getCandidates() const {
    FlushContext::List result;
    result.reserve(_num_candidates);
    for (const auto& candidate : _candidates) {
        if (result.size() < _num_candidates || candidate.get_always_flush()) {
            result.emplace_back(candidate.get_flush_context());
        }
    }
    return result;
}

} // namespace proton
