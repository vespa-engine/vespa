// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_engine_explorer.h"

#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/inserter.h>

using vespalib::slime::Cursor;
using vespalib::slime::Inserter;
using vespalib::StateExplorer;
using searchcorespi::IFlushTarget;

namespace proton {

namespace {

void
convertToSlime(const FlushEngine::FlushMetaSet &flushingTargets, Cursor &array)
{
    for (const auto &target : flushingTargets) {
        Cursor &object = array.addObject();
        object.setString("name", target.getName());
        object.setString("startTime", target.getStart().toString());
        fastos::TimeStamp elapsedTime = target.elapsed();
        object.setDouble("elapsedTime", elapsedTime.sec());
    }
}

void
sortTargetList(FlushContext::List &allTargets)
{
    std::sort(allTargets.begin(), allTargets.end(),
            [](const FlushContext::SP &rhs, const FlushContext::SP &lhs) {
                return rhs->getTarget()->getFlushedSerialNum() <
                        lhs->getTarget()->getFlushedSerialNum();
    });
}

void
convertToSlime(const FlushContext::List &allTargets,
               const fastos::UTCTimeStamp &now,
               Cursor &array)
{
    for (const auto &ctx : allTargets) {
        Cursor &object = array.addObject();
        object.setString("name", ctx->getName());
        const IFlushTarget::SP &target = ctx->getTarget();
        object.setLong("flushedSerialNum", target->getFlushedSerialNum());
        object.setLong("memoryGain", target->getApproxMemoryGain().gain());
        object.setLong("diskGain", target->getApproxDiskGain().gain());
        object.setString("lastFlushTime", target->getLastFlushTime().toString());
        fastos::TimeStamp timeSinceLastFlush = now - target->getLastFlushTime();
        object.setDouble("timeSinceLastFlush", timeSinceLastFlush.sec());
        object.setBool("needUrgentFlush", target->needUrgentFlush());
    }
}

}

FlushEngineExplorer::FlushEngineExplorer(const FlushEngine &engine)
    : _engine(engine)
{
}

void
FlushEngineExplorer::get_state(const Inserter &inserter, bool full) const
{
    Cursor &object = inserter.insertObject();
    if (full) {
        fastos::UTCTimeStamp now = fastos::ClockSystem::now();
        convertToSlime(_engine.getCurrentlyFlushingSet(), object.setArray("flushingTargets"));
        FlushContext::List allTargets = _engine.getTargetList(true);
        sortTargetList(allTargets);
        convertToSlime(allTargets, now, object.setArray("allTargets"));
    }
}

} // namespace proton
