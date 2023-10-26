// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "prepare_restart_flush_strategy.h"
#include <memory>
#include <vespa/searchlib/common/serialnum.h>

namespace proton {

class FlushContext;

/**
 * Class describing a flush target candidate for the prepare restart flush strategy.
 */
class FlushTargetCandidate
{
    std::shared_ptr<FlushContext> _flush_context;
    double                        _replay_operation_cost;
    search::SerialNum             _flushed_serial;
    search::SerialNum             _current_serial;
    double                        _replay_cost;
    uint64_t                      _approx_bytes_to_write_to_disk;
    double                        _write_cost;
    bool                          _always_flush;

    using Config = PrepareRestartFlushStrategy::Config;
public:
    FlushTargetCandidate(std::shared_ptr<FlushContext> flush_context, search::SerialNum current_serial, const Config &cfg);
    ~FlushTargetCandidate();
    const std::shared_ptr<FlushContext> &get_flush_context() const { return _flush_context; }
    search::SerialNum  get_flushed_serial() const { return _flushed_serial; }
    double get_write_cost() const { return _write_cost; }
    bool get_always_flush() const { return _always_flush; }
};

}
