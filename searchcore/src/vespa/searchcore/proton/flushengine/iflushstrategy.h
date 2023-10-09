// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "iflushhandler.h"
#include "flushcontext.h"

namespace proton {

namespace flushengine {
class ActiveFlushStats;
class TlsStatsMap;
}

/**
 * This class represents a strategy used by the FlushEngine to make decisions on
 * when and what to flush.
 */
class IFlushStrategy {
public:
    using SP = std::shared_ptr<IFlushStrategy>;

    IFlushStrategy(const IFlushStrategy &) = delete;
    IFlushStrategy & operator = (const IFlushStrategy &) = delete;

    virtual ~IFlushStrategy() = default;

    /**
     * Takes an input of targets that are candidates for flush and returns
     * a list of targets sorted according to priority strategy.
     * @param targetList The list of possible flush targets.
     * @param tlsStatsMap Statistics per domain in the TLS. A domain matches a flush handler.
     * @parma active_flushes Statistics of active (ongoing) flushes per flush handler.
     * @return A prioritized list of targets to flush.
     */
    virtual FlushContext::List getFlushTargets(const FlushContext::List& targetList,
                                               const flushengine::TlsStatsMap& tlsStatsMap,
                                               const flushengine::ActiveFlushStats& active_flushes) const = 0;
protected:
    IFlushStrategy() = default;
};

}

