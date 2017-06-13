// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_target_candidates.h"
#include "tls_stats.h"

namespace proton {

using search::SerialNum;

using Config = PrepareRestartFlushStrategy::Config;

namespace {

SerialNum
calculateReplayStartSerial(const FlushContext::List &sortedFlushContexts,
                           size_t numCandidates,
                           const flushengine::TlsStats &tlsStats)
{
    if (numCandidates == 0) {
        return tlsStats.getFirstSerial();
    }
    if (numCandidates == sortedFlushContexts.size()) {
        return tlsStats.getLastSerial() + 1;
    }
    return sortedFlushContexts[numCandidates]->getTarget()->getFlushedSerialNum() + 1;
}

double
calculateTlsReplayCost(const flushengine::TlsStats &tlsStats,
                       const Config &cfg,
                       SerialNum replayStartSerial)
{
    SerialNum replayEndSerial = tlsStats.getLastSerial();
    SerialNum numTotalOperations = replayEndSerial - tlsStats.getFirstSerial() + 1;
    if (numTotalOperations == 0) {
        return 0;
    }
    double numBytesPerOperation =
        (double)tlsStats.getNumBytes() / (double)numTotalOperations;
    SerialNum numOperationsToReplay = replayEndSerial + 1 - replayStartSerial;
    double numBytesToReplay = numBytesPerOperation * numOperationsToReplay;
    return numBytesToReplay * cfg.tlsReplayCost;
}

double
calculateFlushTargetsWriteCost(const FlushContext::List &sortedFlushContexts,
                               size_t numCandidates,
                               const Config &cfg)
{
    double result = 0;
    for (size_t i = 0; i < numCandidates; ++i) {
        const auto &flushContext = sortedFlushContexts[i];
        result += (flushContext->getTarget()->getApproxBytesToWriteToDisk() *
                cfg.flushTargetWriteCost);
    }
    return result;
}

}

FlushTargetCandidates::FlushTargetCandidates(const FlushContext::List &sortedFlushContexts,
                                             size_t numCandidates,
                                             const flushengine::TlsStats &tlsStats,
                                             const Config &cfg)
    : _sortedFlushContexts(&sortedFlushContexts),
      _numCandidates(numCandidates),
      _tlsReplayCost(calculateTlsReplayCost(tlsStats,
                                            cfg,
                                            calculateReplayStartSerial(sortedFlushContexts,
                                                                       numCandidates,
                                                                       tlsStats))),
      _flushTargetsWriteCost(calculateFlushTargetsWriteCost(sortedFlushContexts,
                                                            numCandidates,
                                                            cfg))
{
}

FlushContext::List
FlushTargetCandidates::getCandidates() const
{
    FlushContext::List result(_sortedFlushContexts->begin(),
            _sortedFlushContexts->begin() + _numCandidates);
    return result;
}

} // namespace proton
