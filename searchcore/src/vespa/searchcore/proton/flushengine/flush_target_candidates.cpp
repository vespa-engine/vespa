// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_target_candidates.h"
#include "flush_target_candidate.h"
#include "tls_stats.h"

namespace proton {

using search::SerialNum;

using Config = PrepareRestartFlushStrategy::Config;
using TlsReplayCost = FlushTargetCandidates::TlsReplayCost;

namespace {

SerialNum
calculateReplayStartSerial(vespalib::ConstArrayRef<FlushTargetCandidate> candidates,
                           size_t num_candidates,
                           const flushengine::TlsStats &tlsStats)
{
    if (num_candidates == 0) {
        return tlsStats.getFirstSerial();
    }
    if (num_candidates == candidates.size()) {
        return tlsStats.getLastSerial() + 1;
    }
    return candidates[num_candidates].get_flushed_serial() + 1;
}

TlsReplayCost
calculateTlsReplayCost(const flushengine::TlsStats &tlsStats,
                       const Config &cfg,
                       SerialNum replayStartSerial)
{
    SerialNum replayEndSerial = tlsStats.getLastSerial();
    SerialNum numTotalOperations = replayEndSerial - tlsStats.getFirstSerial() + 1;
    if (numTotalOperations == 0) {
        return TlsReplayCost(0.0, 0.0);
    }
    double numBytesPerOperation =
        (double)tlsStats.getNumBytes() / (double)numTotalOperations;
    SerialNum numOperationsToReplay = replayEndSerial + 1 - replayStartSerial;
    double numBytesToReplay = numBytesPerOperation * numOperationsToReplay;
    return TlsReplayCost((numBytesToReplay * cfg.tlsReplayByteCost), (numOperationsToReplay * cfg.tlsReplayOperationCost));
}

double
calculateFlushTargetsWriteCost(vespalib::ConstArrayRef<FlushTargetCandidate> candidates,
                               size_t num_candidates)
{
    double result = 0;
    for (size_t i = 0; i < num_candidates; ++i) {
        result += candidates[i].get_write_cost();
    }
    return result;
}

}

FlushTargetCandidates::FlushTargetCandidates(vespalib::ConstArrayRef<FlushTargetCandidate> candidates,
                                             size_t num_candidates,
                                             const flushengine::TlsStats &tlsStats,
                                             const Config &cfg)
    : _candidates(candidates),
      _num_candidates(std::min(num_candidates, _candidates.size())),
      _tlsReplayCost(calculateTlsReplayCost(tlsStats,
                                            cfg,
                                            calculateReplayStartSerial(_candidates,
                                                                       _num_candidates,
                                                                       tlsStats))),
      _flushTargetsWriteCost(calculateFlushTargetsWriteCost(_candidates,
                                                            _num_candidates))
{
}

FlushContext::List
FlushTargetCandidates::getCandidates() const
{
    FlushContext::List result;
    result.reserve(_num_candidates);
    for (const auto &candidate : _candidates) {
        if (result.size() < _num_candidates || candidate.get_always_flush()) {
            result.emplace_back(candidate.get_flush_context());
        }
    }
    return result;
}

} // namespace proton
