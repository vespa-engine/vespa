// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "prepare_restart_costs.h"

#include <memory>

namespace proton {

class FlushContext;

/**
 * Class describing a flush target candidate for the prepare restart flush strategy.
 */
class FlushTargetCandidate {
    std::shared_ptr<FlushContext>    _flush_context;
    search::SerialNum                _flushed_serial;
    flushengine::PrepareRestartCosts _prepare_restart_costs;
    bool                             _always_flush;

public:
    FlushTargetCandidate(std::shared_ptr<FlushContext> flush_context, search::SerialNum current_serial,
                         const flushengine::PrepareRestartCostsConfig& cfg) noexcept;
    ~FlushTargetCandidate();
    [[nodiscard]] const std::shared_ptr<FlushContext>& get_flush_context() const noexcept { return _flush_context; }
    [[nodiscard]] search::SerialNum get_flushed_serial() const noexcept { return _flushed_serial; }
    [[nodiscard]] double replay_cost() const noexcept { return _prepare_restart_costs.replay_cost(); }
    [[nodiscard]] double get_write_cost() const noexcept { return _prepare_restart_costs.write_cost(); }
    [[nodiscard]] double get_read_cost() const noexcept { return _prepare_restart_costs.read_cost(); }
    [[nodiscard]] bool get_always_flush() const noexcept { return _always_flush; }
};

} // namespace proton
