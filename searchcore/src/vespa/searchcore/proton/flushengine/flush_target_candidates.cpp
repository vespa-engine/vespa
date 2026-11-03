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

} // namespace

FlushTargetCandidates::FlushTargetCandidates(std::span<const FlushTargetCandidate> candidates,
                                             const flushengine::TlsStats&          tlsStats,
                                             const PrepareRestartCostsConfig&      cfg) noexcept
    : _candidates(candidates),
      _num_candidates(0),
      _tlsReplayCost(calculateTlsReplayCost(tlsStats, cfg, calculateReplayStartSerial(_candidates, 0, tlsStats))),
      _flush_targets_replay_cost(0.0),
      _flushTargetsWriteCost(0.0),
      _flush_targets_read_cost(0.0),
      _tls_stats(&tlsStats),
      _prepare_restart_costs_config(&cfg) {
    for (const auto& candidate : candidates) {
        if (candidate.get_always_flush()) {
            _flushTargetsWriteCost += candidate.get_write_cost();
            _flush_targets_read_cost += candidate.get_read_cost();
        } else {
            _flush_targets_replay_cost += candidate.replay_cost();
        }
    }
}

void FlushTargetCandidates::inc_num_candidates() noexcept {
    if (_num_candidates < _candidates.size()) {
        auto& candidate = _candidates[_num_candidates];
        ++_num_candidates;
        _tlsReplayCost =
            calculateTlsReplayCost(*_tls_stats, *_prepare_restart_costs_config,
                                   calculateReplayStartSerial(_candidates, _num_candidates, *_tls_stats));
        if (!candidate.get_always_flush()) {
            _flush_targets_replay_cost -= candidate.replay_cost();
            _flushTargetsWriteCost += candidate.get_write_cost();
            _flush_targets_read_cost += candidate.get_read_cost();
        }
    }
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
