// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "result_processor.h"
#include "matching_stats.h"

namespace vespalib { struct ThreadBundle; }
namespace search { class FeatureSet; }

namespace proton::matching {

class MatchToolsFactory;
struct MatchParams;

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
                                      const MatchToolsFactory &mtf,
                                      ResultProcessor &resultProcessor,
                                      uint32_t distributionKey,
                                      uint32_t numSearchPartitions);

    static std::shared_ptr<search::FeatureSet>
    getFeatureSet(const MatchToolsFactory &matchToolsFactory,
                  const std::vector<uint32_t> &docs, bool summaryFeatures);
    static MatchingStats getStats(MatchMaster && rhs) { return std::move(rhs._stats); }
};

}
