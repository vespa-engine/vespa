// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "iflushstrategy.h"

namespace proton {

/*
 * Class implementing "flush" everything strategy.  Targets are just
 * sorted on age.
 */
class FlushAllStrategy : public IFlushStrategy
{
public:
    FlushAllStrategy();

    FlushContext::List
    getFlushTargets(const FlushContext::List &targetList,
                    const flushengine::TlsStatsMap&,
                    const flushengine::ActiveFlushStats&) const override;
};

} // namespace proton
