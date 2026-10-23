// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_target_candidate.h"

#include "flushcontext.h"

using proton::flushengine::PrepareRestartCostsConfig;

namespace proton {

FlushTargetCandidate::FlushTargetCandidate(std::shared_ptr<FlushContext>    flush_context,
                                           search::SerialNum                current_serial,
                                           const PrepareRestartCostsConfig& cfg) noexcept
    : _flush_context(std::move(flush_context)),
      _flushed_serial(_flush_context->getTarget()->getFlushedSerialNum()),
      _prepare_restart_costs(*_flush_context->getTarget(), current_serial, _flushed_serial, cfg),
      _always_flush(_prepare_restart_costs.replay_cost() >=
                    _prepare_restart_costs.write_cost() + _prepare_restart_costs.read_cost()) {
}

FlushTargetCandidate::~FlushTargetCandidate() = default;

} // namespace proton
