// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <memory>

namespace proton::flushengine {

class FlushHistory;
class FlushStrategyIdNotifier;

/*
 * Result of a call to FlushEngine::set_strategy. If _wait_strategy_id is zero then the flush engine could not set the
 * strategy.
 */
class SetStrategyResult {
    uint32_t                                 _wait_strategy_id;
    std::shared_ptr<FlushStrategyIdNotifier> _lowest_strategy_id_notifier;
    std::shared_ptr<FlushHistory>            _flush_history;
public:
    SetStrategyResult();
    SetStrategyResult(uint32_t wait_strategy_id_in,
                      std::shared_ptr<FlushStrategyIdNotifier> lowest_strategy_id_notifier_in,
                      std::shared_ptr<FlushHistory> flush_history_in);
    SetStrategyResult(const SetStrategyResult&);
    SetStrategyResult(SetStrategyResult&&) noexcept = default;
    ~SetStrategyResult();
    SetStrategyResult& operator=(const SetStrategyResult&);
    SetStrategyResult& operator=(SetStrategyResult&&) noexcept = default;
    uint32_t wait_strategy_id() const noexcept { return _wait_strategy_id; }
    const std::shared_ptr<FlushStrategyIdNotifier>& lowest_strategy_id_notifier() const noexcept { return _lowest_strategy_id_notifier; }
    const std::shared_ptr<FlushHistory>& flush_history() const noexcept { return _flush_history; }
};

}
