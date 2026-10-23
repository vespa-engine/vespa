// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_engine_explorer.h"

#include "flush_history_explorer.h"
#include "flushengine.h"
#include "i_tls_stats_factory.h"
#include "prepare_restart_costs.h"
#include "tls_stats_map.h"

#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/inserter.h>

using proton::flushengine::FlushHistoryExplorer;
using proton::flushengine::PrepareRestartCosts;
using proton::flushengine::PrepareRestartCostsConfig;
using proton::flushengine::TlsStatsMap;
using searchcorespi::IFlushTarget;
using vespalib::StateExplorer;
using vespalib::slime::Cursor;
using vespalib::slime::Inserter;

namespace proton {

namespace {

void convertToSlime(const FlushEngine::FlushMetaSet& flushingTargets, Cursor& array) {
    for (const auto& target : flushingTargets) {
        Cursor& object = array.addObject();
        object.setString("name", target.getName());
        object.setString("startTime", vespalib::to_string(target.getStart()));
        object.setDouble("elapsedTime", vespalib::to_s(target.elapsed()));
    }
}

void sortTargetList(FlushContext::List& allTargets) {
    std::sort(allTargets.begin(), allTargets.end(), [](const FlushContext::SP& rhs, const FlushContext::SP& lhs) {
        return rhs->getTarget()->getFlushedSerialNum() < lhs->getTarget()->getFlushedSerialNum();
    });
}

void convert_prepare_restart_costs(const PrepareRestartCosts& prepare_restart_costs, Cursor& object) {
    object.setDouble("read_cost", prepare_restart_costs.read_cost());
    object.setDouble("write_cost", prepare_restart_costs.write_cost());
    object.setDouble("replay_cost", prepare_restart_costs.replay_cost());
}

void convertToSlime(const FlushContext::List& allTargets, const TlsStatsMap& tls_stats_map,
                    const PrepareRestartCostsConfig& prepare_restart_costs_config, const vespalib::system_time& now,
                    Cursor& array) {
    for (const auto& ctx : allTargets) {
        Cursor& object = array.addObject();
        object.setString("name", ctx->getName());
        const IFlushTarget::SP& target = ctx->getTarget();
        object.setLong("flushedSerialNum", target->getFlushedSerialNum());
        object.setLong("memoryGain", target->getApproxMemoryGain().gain());
        object.setLong("diskGain", target->getApproxDiskGain().gain());
        object.setString("lastFlushTime", vespalib::to_string(target->getLastFlushTime()));
        vespalib::duration timeSinceLastFlush = now - target->getLastFlushTime();
        object.setDouble("timeSinceLastFlush", vespalib::to_s(timeSinceLastFlush));
        object.setBool("needUrgentFlush", target->needUrgentFlush());
        object.setLong("last_flush_duration",
                       duration_cast<std::chrono::microseconds>(target->last_flush_duration()).count());
        object.setLong("estimated_flush_duration",
                       duration_cast<std::chrono::microseconds>(target->estimated_flush_duration()).count());
        object.setLong("reserved-memory-for-flush", target->reserved_memory_for_flush());
        if (target->getType() != IFlushTarget::Type::GC) {
            const auto&         tls_stats = tls_stats_map.getTlsStats(ctx->getHandler()->getName());
            PrepareRestartCosts prepare_restart_costs(*target, tls_stats.getLastSerial(),
                                                      target->getFlushedSerialNum(), prepare_restart_costs_config);
            convert_prepare_restart_costs(prepare_restart_costs, object.setObject("prepare-restart-costs"));
        }
    }
}

const std::string FLUSH_HISTORY("flush_history");

} // namespace

FlushEngineExplorer::FlushEngineExplorer(const FlushEngine&                     engine,
                                         flushengine::PrepareRestartCostsConfig prepare_restart_costs_config)
    : _engine(engine), _prepare_restart_costs_config(prepare_restart_costs_config) {
}

void FlushEngineExplorer::get_state(const Inserter& inserter, bool full) const {
    Cursor& object = inserter.insertObject();
    if (full) {
        vespalib::system_time now = vespalib::system_clock::now();
        convertToSlime(_engine.getCurrentlyFlushingSet(), object.setArray("flushingTargets"));
        FlushContext::List allTargets = _engine.getTargetList(true);
        sortTargetList(allTargets);
        auto tls_stats_map = _engine.get_tls_stats_factory().create();
        convertToSlime(allTargets, tls_stats_map, _prepare_restart_costs_config, now, object.setArray("allTargets"));
    }
}

std::vector<std::string> FlushEngineExplorer::get_children_names() const {
    return {FLUSH_HISTORY};
}

std::unique_ptr<vespalib::StateExplorer> FlushEngineExplorer::get_child(std::string_view name) const {
    if (name == FLUSH_HISTORY) {
        return std::make_unique<FlushHistoryExplorer>(_engine.get_flush_history());
    }
    return {};
}

} // namespace proton
