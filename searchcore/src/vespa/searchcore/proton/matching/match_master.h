// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/clock.h>
#include <vespa/vespalib/util/thread_bundle.h>
#include <vespa/searchlib/common/featureset.h>
#include "result_processor.h"
#include "match_params.h"
#include "matching_stats.h"

namespace proton {
namespace matching {

class MatchToolsFactory;

/**
 * Handles overall matching and keeps track of match threads.
 **/
class MatchMaster
{
private:
    MatchingStats _stats;

public:
    const MatchingStats &getStats() const { return _stats; }
    ResultProcessor::Result::UP match(const MatchParams &params,
                                      vespalib::ThreadBundle &threadBundle,
                                      const MatchToolsFactory &matchToolsFactory,
                                      ResultProcessor &resultProcessor,
                                      uint32_t distributionKey,
                                      uint32_t numSearchPartitions);

    static search::FeatureSet::SP
    getFeatureSet(const MatchToolsFactory &matchToolsFactory,
                  const std::vector<uint32_t> &docs, bool summaryFeatures);
};

} // namespace proton::matching
} // namespace proton

