// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memoryflush.h"
#include <vespa/searchcore/proton/flushengine/active_flush_stats.h>
#include <vespa/searchcore/proton/flushengine/tls_stats_map.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/time.h>
#include <cinttypes>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.memoryflush");

using search::SerialNum;
using proton::flushengine::TlsStats;
using searchcorespi::IFlushTarget;

namespace proton {

namespace {

int64_t
getSerialDiff(SerialNum localLastSerial, const IFlushTarget &target)
{
    return localLastSerial - target.getFlushedSerialNum();
}

uint64_t
estimateNeededTlsSizeForFlushTarget(const TlsStats &tlsStats, SerialNum flushedSerialNum)
{
    if (flushedSerialNum < tlsStats.getFirstSerial()) {
        return tlsStats.getNumBytes();
    }
    int64_t numEntries = tlsStats.getLastSerial() - tlsStats.getFirstSerial() + 1;
    if (numEntries <= 0) {
        return 0u;
    }
    if (flushedSerialNum >= tlsStats.getLastSerial()) {
        return 0u;
    }
    double bytesPerEntry = static_cast<double>(tlsStats.getNumBytes()) / numEntries;
    return bytesPerEntry * (tlsStats.getLastSerial() - flushedSerialNum);
}

}

MemoryFlush::Config::Config()
    : maxGlobalMemory(4000_Mi),
      maxGlobalTlsSize(20_Gi),
      globalDiskBloatFactor(0.2),
      maxMemoryGain(1000_Mi),
      diskBloatFactor(0.2),
      maxTimeGain(std::chrono::hours(24))
{ }


MemoryFlush::Config::Config(uint64_t maxGlobalMemory_in,
                            uint64_t maxGlobalTlsSize_in,
                            double globalDiskBloatFactor_in,
                            uint64_t maxMemoryGain_in,
                            double diskBloatFactor_in,
                            vespalib::duration maxTimeGain_in)
    : maxGlobalMemory(maxGlobalMemory_in),
      maxGlobalTlsSize(maxGlobalTlsSize_in),
      globalDiskBloatFactor(globalDiskBloatFactor_in),
      maxMemoryGain(maxMemoryGain_in),
      diskBloatFactor(diskBloatFactor_in),
      maxTimeGain(maxTimeGain_in)
{ }

vespalib::string
MemoryFlush::Config::toString() const {
    vespalib::asciistream os;
    os << "maxGlobalMemory=" << maxGlobalMemory << " ";
    os << "maxGlobalTlsSize=" << maxGlobalTlsSize << " ";
    os << "globalDiskBloatFactor=" << globalDiskBloatFactor << " ";
    os << "maxMemoryGain=" << maxMemoryGain << " ";
    os << "diskBloatFactor=" << diskBloatFactor << " ";
    os << "maxTimeGain(ns)=" << maxTimeGain.count();
    return os.str();
}

MemoryFlush::MemoryFlush(const Config &config, vespalib::system_time startTime)
    : _lock(),
      _config(config),
      _startTime(startTime)
{ }


MemoryFlush::MemoryFlush()
    : MemoryFlush(Config(), vespalib::system_clock::now())
{ }

MemoryFlush::~MemoryFlush() = default;

MemoryFlush::Config
MemoryFlush::getConfig() const
{
    std::lock_guard<std::mutex> guard(_lock);
    return _config;
}

void
MemoryFlush::setConfig(const Config &config)
{
    std::lock_guard<std::mutex> guard(_lock);
    _config = config;
}

namespace {

vespalib::string
getOrderName(MemoryFlush::OrderType &orderType)
{
    switch (orderType) {
        case MemoryFlush::OrderType::MEMORY: return "MEMORY";
        case MemoryFlush::OrderType::TLSSIZE: return "TLSSIZE";
        case MemoryFlush::OrderType::DISKBLOAT: return "DISKBLOAT";
        case MemoryFlush::OrderType::MAXAGE: return "MAXAGE";
        case MemoryFlush::OrderType::DEFAULT: return "DEFAULT";
    }
    return "DEFAULT";
}

size_t
computeGain(const IFlushTarget::DiskGain & gain) {
    return std::max(INT64_C(100000000), std::max(gain.getBefore(), gain.getAfter()));
}

}

FlushContext::List
MemoryFlush::getFlushTargets(const FlushContext::List& targetList,
                             const flushengine::TlsStatsMap& tlsStatsMap,
                             const flushengine::ActiveFlushStats& active_flushes) const
{
    OrderType order(DEFAULT);
    uint64_t totalMemory(0);
    IFlushTarget::DiskGain totalDisk;
    uint64_t totalTlsSize(0);
    const Config config(getConfig());
    vespalib::hash_set<const void *> visitedHandlers;
    vespalib::system_time now(vespalib::system_clock::now());
    LOG(debug,
        "getFlushTargets(): globalMaxMemory(%" PRIu64 "), maxGlobalTlsSize(%" PRIu64 "), globalDiskBloatFactor(%f), "
        "maxMemoryGain(%" PRIu64 "), diskBloatFactor(%f), maxTimeGain(%f), startTime(%f)",
        config.maxGlobalMemory, config.maxGlobalTlsSize, config.globalDiskBloatFactor,
        config.maxMemoryGain, config.diskBloatFactor,
        vespalib::to_s(config.maxTimeGain),
        vespalib::to_s(_startTime.time_since_epoch()));
    for (const auto & ctx : targetList) {
        const IFlushTarget & target(*ctx->getTarget());
        const IFlushHandler & handler(*ctx->getHandler());
        int64_t mgain(std::max(INT64_C(0), target.getApproxMemoryGain().gain()));
        const IFlushTarget::DiskGain dgain(target.getApproxDiskGain());
        totalDisk += dgain;
        SerialNum localLastSerial = ctx->getLastSerial();
        int64_t serialDiff = getSerialDiff(localLastSerial, target);
        vespalib::system_time lastFlushTime = target.getLastFlushTime();
        vespalib::duration timeDiff(now - (lastFlushTime > vespalib::system_time() ? lastFlushTime : _startTime));
        totalMemory += mgain;
        const flushengine::TlsStats &tlsStats = tlsStatsMap.getTlsStats(handler.getName());

        auto oldest_start_time = active_flushes.oldest_start_time(handler.getName());
        // Don't consider TLSSIZE if there exists an active (ongoing) flush (for this flush handler)
        // that started before the last flush time of the flush target to evaluate.
        // Instead we should wait for the active (ongoing) flush to be finished before doing another evaluation.
        if (!oldest_start_time.has_value() || lastFlushTime < oldest_start_time.value()) {
            if (visitedHandlers.insert(&handler).second) {
                totalTlsSize += tlsStats.getNumBytes();
                if ((totalTlsSize > config.maxGlobalTlsSize) && (order < TLSSIZE)) {
                    order = TLSSIZE;
                }
            }
        }
        if ((mgain >= config.maxMemoryGain) && (order < MEMORY)) {
            order = MEMORY;
        } else if ((dgain.gain() > config.diskBloatFactor * computeGain(dgain)) && (order < DISKBLOAT)) {
            order = DISKBLOAT;
        } else if ((timeDiff >= config.maxTimeGain) && (order < MAXAGE)) {
            order = MAXAGE;
        }
        LOG(debug,
            "getFlushTargets(): target(%s), totalMemoryGain(%" PRIu64 "), memoryGain(%" PRIu64 "), "
            "totalDiskGain(%" PRId64 "), diskGain(%" PRId64 "), "
            "tlsSize(%" PRIu64 "), tlsSizeNeeded(%" PRIu64 "), "
            "flushedSerial(%" PRIu64 "), localLastSerial(%" PRIu64 "), serialDiff(%" PRId64 "), "
            "lastFlushTime(%fs), nowTime(%fs), timeDiff(%fs), order(%s)",
            ctx->getName().c_str(), totalMemory, mgain,
            totalDisk.gain(), dgain.gain(),
            tlsStats.getNumBytes(), estimateNeededTlsSizeForFlushTarget(tlsStats, target.getFlushedSerialNum()),
            target.getFlushedSerialNum(), localLastSerial, serialDiff,
            vespalib::to_s(lastFlushTime.time_since_epoch()),
            vespalib::to_s(now.time_since_epoch()),
            vespalib::to_s(timeDiff),
            getOrderName(order).c_str());
    }
    if (!targetList.empty()) {
        if ((totalMemory >= config.maxGlobalMemory) && (order < MEMORY)) {
            order = MEMORY;
        }
        if ((totalDisk.gain() > config.globalDiskBloatFactor * computeGain(totalDisk)) && (order < DISKBLOAT)) {
            order = DISKBLOAT;
        }
    }
    FlushContext::List fv(targetList);
    std::sort(fv.begin(), fv.end(), CompareTarget(order, tlsStatsMap));
    // No desired order and no urgent needs; no flush required at this moment.
    if (order == DEFAULT &&
        !fv.empty() &&
        !fv[0]->getTarget()->needUrgentFlush()) {
        LOG(debug, "getFlushTargets(): empty list");
        return FlushContext::List();
    }
    if (LOG_WOULD_LOG(debug)) {
        vespalib::asciistream oss;
        for (size_t i = 0; i < fv.size(); ++i) {
            if (i > 0) {
                oss << ",";
            }
            oss << fv[i]->getName();
        }
        LOG(debug, "getFlushTargets(): %zu sorted targets: [%s]", fv.size(), oss.str().data());
    }
    return fv;
}


bool
MemoryFlush::CompareTarget::operator()(const FlushContext::SP &lfc, const FlushContext::SP &rfc) const
{
    const IFlushTarget &lhs = *lfc->getTarget();
    const IFlushTarget &rhs = *rfc->getTarget();
    if (lhs.needUrgentFlush() != rhs.needUrgentFlush()) {
        return lhs.needUrgentFlush();
    }

    switch (_order) {
    case MEMORY:
        return (lhs.getApproxMemoryGain().gain() > rhs.getApproxMemoryGain().gain());
    case TLSSIZE: {
        const flushengine::TlsStats &lhsTlsStats = _tlsStatsMap.getTlsStats(lfc->getHandler()->getName());
        const flushengine::TlsStats &rhsTlsStats = _tlsStatsMap.getTlsStats(rfc->getHandler()->getName());
        SerialNum lhsFlushedSerialNum(lhs.getFlushedSerialNum());
        SerialNum rhsFlushedSerialNum(rhs.getFlushedSerialNum());
        uint64_t lhsNeededTlsSize = estimateNeededTlsSizeForFlushTarget(lhsTlsStats, lhsFlushedSerialNum);
        uint64_t rhsNeededTlsSize = estimateNeededTlsSizeForFlushTarget(rhsTlsStats, rhsFlushedSerialNum);
        if (lhsNeededTlsSize != rhsNeededTlsSize) {
            return (lhsNeededTlsSize > rhsNeededTlsSize);
        }
        return (lhs.getLastFlushTime() < rhs.getLastFlushTime());
    }
    case DISKBLOAT:
        return (lhs.getApproxDiskGain().gain() > rhs.getApproxDiskGain().gain());
    case MAXAGE:
        return (lhs.getLastFlushTime() < rhs.getLastFlushTime());
    default:
        return (getSerialDiff(lfc->getLastSerial(), lhs) > getSerialDiff(rfc->getLastSerial(), rhs));
    }
}

} // namespace proton
