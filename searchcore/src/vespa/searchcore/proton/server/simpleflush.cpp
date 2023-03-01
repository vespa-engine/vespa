// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simpleflush.h"
#include <algorithm>
#include <cinttypes>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.simpleflush");

namespace proton {

SimpleFlush::SimpleFlush() = default;

FlushContext::List
SimpleFlush::getFlushTargets(const FlushContext::List& targetList,
                             const flushengine::TlsStatsMap&,
                             const flushengine::ActiveFlushStats&) const
{
    FlushContext::List fv(targetList);
    std::sort(fv.begin(), fv.end(), CompareTarget());
    return fv;
}

bool
SimpleFlush::CompareTarget::compare(const IFlushTarget & lhs, const IFlushTarget & rhs) const
{
    IFlushTarget::MemoryGain lhsMgain(lhs.getApproxMemoryGain());
    IFlushTarget::SerialNum lhsSerial(lhs.getFlushedSerialNum());

    IFlushTarget::MemoryGain rhsMgain(rhs.getApproxMemoryGain());
    IFlushTarget::SerialNum rhsSerial(rhs.getFlushedSerialNum());


    bool ret = lhsSerial < rhsSerial;
    LOG(spam, "SimpleFlush::compare("
        "[name = '%s', before = %" PRIu64 ", after = %" PRIu64 ", serial = %" PRIu64 "], "
        "[name = '%s', before = %" PRIu64 ", after = %" PRIu64 ", serial = %" PRIu64 "]) => %d",
        lhs.getName().c_str(), lhsMgain.getBefore(), lhsMgain.getAfter(), lhsSerial,
        rhs.getName().c_str(), rhsMgain.getBefore(), rhsMgain.getAfter(), rhsSerial,
        ret ? 1 : 0);
    return ret;
}

} // namespace proton
