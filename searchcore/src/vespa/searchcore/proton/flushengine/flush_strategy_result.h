// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <string>
#include <vector>

namespace proton {
class FlushContext;
}

namespace proton::flushengine {

/*
 * This class contains a sorted list of flush contexts and tracking info used for initiating
 * flushing, updating flush history and logging events for flush operations.
 */
class FlushStrategyResult {
    std::vector<std::shared_ptr<FlushContext>> _list;
    std::string                                _strategy_name;
    uint32_t                                   _strategy_id;
    bool                                       _priority_strategy;
    std::string                                _strategy_info;

public:
    explicit FlushStrategyResult(std::vector<std::shared_ptr<FlushContext>> list_in, std::string strategy_name_in,
                                 uint32_t strategy_id_in, bool priority_strategy_in, std::string stratewgy_info_in);
    FlushStrategyResult(const FlushStrategyResult&) = delete;
    FlushStrategyResult(FlushStrategyResult&&) = default;
    ~FlushStrategyResult();
    FlushStrategyResult& operator=(const FlushStrategyResult&) = delete;
    FlushStrategyResult& operator=(FlushStrategyResult&&) = default;
    void drop_non_high_priority_targets();
    [[nodiscard]] const std::vector<std::shared_ptr<FlushContext>>& list() const noexcept { return _list; }
    [[nodiscard]] const std::string& strategy_name() const noexcept { return _strategy_name; }
    [[nodiscard]] uint32_t strategy_id() const noexcept { return _strategy_id; }
    [[nodiscard]] bool priority_strategy() const noexcept { return _priority_strategy; }
    [[nodiscard]] const std::string& strategy_info() const noexcept { return _strategy_info; }
};

} // namespace proton::flushengine
