// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_all_strategy.h"
#include <algorithm>

using search::SerialNum;
using searchcorespi::IFlushTarget;

namespace proton {

namespace {

class CompareTarget
{
public:
    bool
    operator ()(const FlushContext::SP &lfc,
                const FlushContext::SP &rfc) const;
};

bool
CompareTarget::operator()(const FlushContext::SP &lfc,
                          const FlushContext::SP &rfc) const
{
    const IFlushTarget &lhs = *lfc->getTarget();
    const IFlushTarget &rhs = *rfc->getTarget();
    // Note: This assumes that last flush time is stable while doing sort
    return lhs.getLastFlushTime() < rhs.getLastFlushTime();
}

}

FlushAllStrategy::FlushAllStrategy()
{
}

FlushContext::List
FlushAllStrategy::getFlushTargets(const FlushContext::List &targetList,
                                  const flushengine::TlsStatsMap&,
                                  const flushengine::ActiveFlushStats&) const
{
    if (targetList.empty()) {
        return {};
    }
    FlushContext::List fv(targetList);
    std::sort(fv.begin(), fv.end(), CompareTarget());
    return fv;
}

} // namespace proton
