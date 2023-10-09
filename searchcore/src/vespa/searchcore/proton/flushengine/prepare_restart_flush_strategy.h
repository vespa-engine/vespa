// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "iflushstrategy.h"
#include <vespa/vespalib/stllike/string.h>
#include <map>

namespace proton {

/**
 * Flush strategy that is used to find flush targets to be flushed before a restart.
 *
 * For each flush handler, flush targets are chosen such that the cost of flushing them +
 * the cost of replaying the transaction log after replay is as low as possible.
 *
 * The cost of replaying the transaction log is: the number of bytes to replay * a replay speed factor.
 * The cost of flushing a flush target is: the number of bytes to write * a write speed factor.
 */
class PrepareRestartFlushStrategy : public IFlushStrategy
{
public:
    struct Config
    {
        double tlsReplayByteCost;
        double tlsReplayOperationCost;
        double flushTargetWriteCost;
        Config(double tlsReplayByteCost_,
               double tlsReplayOperationCost_,
               double flushTargetWriteCost_);
    };

private:
    Config _cfg;

public:
    PrepareRestartFlushStrategy(const Config &cfg);

    virtual FlushContext::List getFlushTargets(const FlushContext::List &targetList,
                                               const flushengine::TlsStatsMap &tlsStatsMap,
                                               const flushengine::ActiveFlushStats&) const override;
};

} // namespace proton
