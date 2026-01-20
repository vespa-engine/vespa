// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "set_strategy_result.h"
#include "flush_strategy_id_notifier.h"

namespace proton::flushengine {

SetStrategyResult::SetStrategyResult()
    : SetStrategyResult(0u, {}, {})
{
}

SetStrategyResult::SetStrategyResult(uint32_t wait_strategy_id_in,
                                     std::shared_ptr<FlushStrategyIdNotifier> lowest_strategy_id_notifier_in,
                                     std::shared_ptr<FlushHistory> flush_history_in)
    : _wait_strategy_id(wait_strategy_id_in),
      _lowest_strategy_id_notifier(std::move(lowest_strategy_id_notifier_in)),
      _flush_history(std::move(flush_history_in))
{
}

SetStrategyResult::SetStrategyResult(const SetStrategyResult&) = default;

SetStrategyResult::~SetStrategyResult() = default;

SetStrategyResult &SetStrategyResult::operator=(const SetStrategyResult&) = default;

void
SetStrategyResult::wait()
{
    /*
     * Wait for flushes started before the strategy change, for
     * flushes initiated by the strategy, and for flush engine to call
     * prune() afterwards.
     */
    if (_wait_strategy_id != 0u) {
        _lowest_strategy_id_notifier->wait_gt_strategy_id(_wait_strategy_id);
    }
}

}
