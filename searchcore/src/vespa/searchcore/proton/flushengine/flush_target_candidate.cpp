// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_target_candidate.h"
#include "flushcontext.h"

namespace proton {

FlushTargetCandidate::FlushTargetCandidate(std::shared_ptr<FlushContext> flush_context, search::SerialNum current_serial, const Config &cfg)
    : _flush_context(std::move(flush_context)),
      _replay_operation_cost(_flush_context->getTarget()->get_replay_operation_cost() * cfg.tlsReplayOperationCost),
      _flushed_serial(_flush_context->getTarget()->getFlushedSerialNum()),
      _current_serial(current_serial),
      _replay_cost(_replay_operation_cost * (_current_serial - _flushed_serial)),
      _approx_bytes_to_write_to_disk(_flush_context->getTarget()->getApproxBytesToWriteToDisk()),
      _write_cost(_approx_bytes_to_write_to_disk * cfg.flushTargetWriteCost),
      _always_flush(_replay_cost >= _write_cost)
{
}

FlushTargetCandidate::~FlushTargetCandidate() = default;

}
