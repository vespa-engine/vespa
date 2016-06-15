// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/flushengine/iflushhandler.h>
#include <vespa/searchcore/proton/flushengine/flushcontext.h>

namespace proton {

namespace flushengine { class TlsStatsMap; }

/**
 * This class represents a strategy used by the FlushEngine to make decisions on
 * when and what to flush.
 */
class IFlushStrategy {
public:
    /**
     * Convenience typedefs.
     */
    typedef std::shared_ptr<IFlushStrategy> SP;

    /**
     * Virtual destructor required for inheritance.
     */
    virtual ~IFlushStrategy() { }

    /**
     * Takes an input of targets that are candidates for flush and returns
     * a list of targets sorted according to priority strategy.
     * @param targetList The list of possible flush targets.
     * @param lastSerial is the last serialnumber known by flushengine.
     * @return A prioritized list of targets to flush.
     */
    virtual FlushContext::List getFlushTargets(const FlushContext::List & targetList,
                                               const flushengine::TlsStatsMap &
                                               tlsStatsMap) const = 0;
};

} // namespace proton

