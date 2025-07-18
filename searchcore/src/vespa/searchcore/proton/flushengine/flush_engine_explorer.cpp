// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_engine_explorer.h"
#include "flush_history_explorer.h"
#include "flushengine.h"
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/inserter.h>

using vespalib::slime::Cursor;
using vespalib::slime::Inserter;
using vespalib::StateExplorer;
using proton::flushengine::FlushHistoryExplorer;
using searchcorespi::IFlushTarget;

namespace proton {

namespace {

void
convertToSlime(const FlushEngine::FlushMetaSet &flushingTargets, Cursor &array)
{
    for (const auto &target : flushingTargets) {
        Cursor &object = array.addObject();
        object.setString("name", target.getName());
        object.setString("startTime", vespalib::to_string(target.getStart()));
        object.setDouble("elapsedTime", vespalib::to_s(target.elapsed()));
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
               const vespalib::system_time &now,
               Cursor &array)
{
    for (const auto &ctx : allTargets) {
        Cursor &object = array.addObject();
        object.setString("name", ctx->getName());
        const IFlushTarget::SP &target = ctx->getTarget();
        object.setLong("flushedSerialNum", target->getFlushedSerialNum());
        object.setLong("memoryGain", target->getApproxMemoryGain().gain());
        object.setLong("diskGain", target->getApproxDiskGain().gain());
        object.setString("lastFlushTime", vespalib::to_string(target->getLastFlushTime()));
        vespalib::duration timeSinceLastFlush = now - target->getLastFlushTime();
        object.setDouble("timeSinceLastFlush", vespalib::to_s(timeSinceLastFlush));
        object.setBool("needUrgentFlush", target->needUrgentFlush());
        object.setLong("last_flush_duration",
                       duration_cast<std::chrono::microseconds>(target->last_flush_duration()).count());
    }
}

const std::string FLUSH_HISTORY("flush_history");

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
        vespalib::system_time now = vespalib::system_clock::now();
        convertToSlime(_engine.getCurrentlyFlushingSet(), object.setArray("flushingTargets"));
        FlushContext::List allTargets = _engine.getTargetList(true);
        sortTargetList(allTargets);
        convertToSlime(allTargets, now, object.setArray("allTargets"));
    }
}

std::vector<std::string>
FlushEngineExplorer::get_children_names() const
{
    return { FLUSH_HISTORY };
}

std::unique_ptr<vespalib::StateExplorer>
FlushEngineExplorer::get_child(std::string_view name) const
{
    if (name == FLUSH_HISTORY) {
        return std::make_unique<FlushHistoryExplorer>(_engine.get_flush_history());
    }
    return {};
}

} // namespace proton
