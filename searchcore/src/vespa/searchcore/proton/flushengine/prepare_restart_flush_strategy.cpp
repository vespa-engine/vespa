// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "prepare_restart_flush_strategy.h"
#include "flush_target_candidates.h"
#include "flush_target_candidate.h"
#include "tls_stats_map.h"
#include <sstream>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".proton.flushengine.prepare_restart_flush_strategy");

namespace proton {

using search::SerialNum;
using searchcorespi::IFlushTarget;

using Config = PrepareRestartFlushStrategy::Config;
using FlushContextsMap = std::map<vespalib::string, FlushContext::List>;
using FlushTargetCandidatesList = std::vector<FlushTargetCandidates::UP>;

PrepareRestartFlushStrategy::Config::Config(double tlsReplayByteCost_,
                                            double tlsReplayOperationCost_,
                                            double flushTargetWriteCost_)
    : tlsReplayByteCost(tlsReplayByteCost_),
      tlsReplayOperationCost(tlsReplayOperationCost_),
      flushTargetWriteCost(flushTargetWriteCost_)
{
}

PrepareRestartFlushStrategy::PrepareRestartFlushStrategy(const Config &cfg)
    : _cfg(cfg)
{
}

namespace {

FlushContext::List
removeGCFlushTargets(const FlushContext::List &flushContexts)
{
    FlushContext::List result;
    for (const auto &flushContext : flushContexts) {
        if (flushContext->getTarget()->getType() != IFlushTarget::Type::GC) {
            result.push_back(flushContext);
        }
    }
    return result;
}

FlushContextsMap
groupByFlushHandler(const FlushContext::List &flushContexts)
{
    FlushContextsMap result;
    for (const auto &flushContext : flushContexts) {
        const vespalib::string &handlerName = flushContext->getHandler()->getName();
        result[handlerName].push_back(flushContext);
    }
    return result;
}

FlushContext::List
flatten(const FlushContextsMap &flushContextsPerHandler)
{
    FlushContext::List result;
    for (const auto &entry : flushContextsPerHandler) {
        for (const auto &flushContext : entry.second) {
            result.push_back(flushContext);
        }
    }
    return result;
}

void
sortByOldestFlushedSerialNumber(std::vector<FlushTargetCandidate>& candidates)
{
    std::sort(candidates.begin(), candidates.end(),
              [](const auto &lhs, const auto &rhs) {
                  if (lhs.get_flushed_serial() == rhs.get_flushed_serial()) {
                      return lhs.get_flush_context()->getName() < rhs.get_flush_context()->getName();
                  }
                  return lhs.get_flushed_serial() < rhs.get_flushed_serial();
              });
}

vespalib::string
toString(const FlushContext::List &flushContexts)
{
    std::ostringstream oss;
    bool first = true;
    for (const auto &flushContext : flushContexts) {
        if (!first) {
            oss << ",";
        }
        oss << "'" << flushContext->getName() << "'";
        first = false;
    }
    return oss.str();
}

FlushContext::List
findBestTargetsToFlush(const FlushContext::List &unsortedFlushContexts,
                       const flushengine::TlsStats &tlsStats,
                       const Config &cfg)
{
    std::vector<FlushTargetCandidate> candidates;
    candidates.reserve(unsortedFlushContexts.size());
    for (const auto &flush_context : unsortedFlushContexts) {
        candidates.emplace_back(flush_context, tlsStats.getLastSerial(), cfg);
    }
    sortByOldestFlushedSerialNumber(candidates);

    FlushTargetCandidates bestSet(candidates, 0, tlsStats, cfg);
    for (size_t numCandidates = 1; numCandidates <= candidates.size(); ++numCandidates) {
        FlushTargetCandidates nextSet(candidates, numCandidates, tlsStats, cfg);
        LOG(debug, "findBestTargetsToFlush(): Created candidate set: "
                "flushTargets=[%s], tlsReplayBytesCost=%f, tlsReplayOperationsCost=%f, flushTargetsWriteCost=%f, totalCost=%f",
                toString(nextSet.getCandidates()).c_str(),
                nextSet.getTlsReplayCost().bytesCost,
                nextSet.getTlsReplayCost().operationsCost,
                nextSet.getFlushTargetsWriteCost(),
                nextSet.getTotalCost());
        if (nextSet.getTotalCost() < bestSet.getTotalCost()) {
            bestSet = nextSet;
        }
    }
    LOG(info, "findBestTargetsToFlush(): Best candidate set: "
            "flushTargets=[%s], tlsReplayBytesCost=%f, tlsReplayOperationsCost=%f, flushTargetsWriteCost=%f, totalCost=%f",
            toString(bestSet.getCandidates()).c_str(),
            bestSet.getTlsReplayCost().bytesCost,
            bestSet.getTlsReplayCost().operationsCost,
            bestSet.getFlushTargetsWriteCost(),
            bestSet.getTotalCost());
    return bestSet.getCandidates();
}

FlushContextsMap
findBestTargetsToFlushPerHandler(const FlushContextsMap &flushContextsPerHandler,
                                 const Config &cfg,
                                 const flushengine::TlsStatsMap &tlsStatsMap)
{
    FlushContextsMap result;
    for (const auto &entry : flushContextsPerHandler) {
        const auto &handlerName = entry.first;
        const auto &flushContexts = entry.second;
        const auto &tlsStats = tlsStatsMap.getTlsStats(handlerName);
        result.insert(std::make_pair(handlerName,
                findBestTargetsToFlush(flushContexts, tlsStats, cfg)));
    }
    return result;
}

}

FlushContext::List
PrepareRestartFlushStrategy::getFlushTargets(const FlushContext::List &targetList,
                                             const flushengine::TlsStatsMap &tlsStatsMap,
                                             const flushengine::ActiveFlushStats&) const
{
    return flatten(findBestTargetsToFlushPerHandler(
            groupByFlushHandler(removeGCFlushTargets(targetList)),
            _cfg, tlsStatsMap));
}

} // namespace proton
