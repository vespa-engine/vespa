// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "set_strategy_result.h"

namespace proton::flushengine {

SetStrategyResult::SetStrategyResult()
    : SetStrategyResult(0u, {}, {})
{
}

SetStrategyResult::SetStrategyResult(uint32_t wait_strategy_id_in,
                                     std::shared_ptr<FlushStrategyIdNotifier> lowest_strategy_id_notifier_in,
                                     std::shared_ptr<FlushHistory> flush_history_in)
    : _wait_strategy_id(wait_strategy_id_in),
      _lowest_strategy_id_notifier(lowest_strategy_id_notifier_in),
      _flush_history(flush_history_in)
{
}

SetStrategyResult::SetStrategyResult(const SetStrategyResult&) = default;

SetStrategyResult::~SetStrategyResult() = default;

SetStrategyResult &SetStrategyResult::operator=(const SetStrategyResult&) = default;

}
