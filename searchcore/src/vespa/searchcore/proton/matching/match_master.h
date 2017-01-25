// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "result_processor.h"
#include "matching_stats.h"

namespace vespalib { class ThreadBundle; }
namespace search { class FeatureSet; }

namespace proton {
namespace matching {

class MatchToolsFactory;
class MatchParams;

/**
 * Handles overall matching and keeps track of match threads.
 **/
class MatchMaster
{
private:
    MatchingStats _stats;

public:
    const MatchingStats & getStats() const { return _stats; }
    ResultProcessor::Result::UP match(const MatchParams &params,
                                      vespalib::ThreadBundle &threadBundle,
                                      const MatchToolsFactory &matchToolsFactory,
                                      ResultProcessor &resultProcessor,
                                      uint32_t distributionKey,
                                      uint32_t numSearchPartitions);

    static std::shared_ptr<search::FeatureSet>
    getFeatureSet(const MatchToolsFactory &matchToolsFactory,
                  const std::vector<uint32_t> &docs, bool summaryFeatures);
    static MatchingStats getStats(MatchMaster && rhs) { return std::move(rhs._stats); }
};

} // namespace proton::matching
} // namespace proton

