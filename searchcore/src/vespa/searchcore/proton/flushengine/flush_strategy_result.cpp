// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flush_strategy_result.h"

#include "flushcontext.h"

#include <algorithm>

using searchcorespi::IFlushTarget;

namespace proton::flushengine {

FlushStrategyResult::FlushStrategyResult(std::vector<std::shared_ptr<FlushContext>> list_in,
                                         std::string strategy_name_in, uint32_t strategy_id_in,
                                         bool priority_strategy_in, std::string strategy_info_in)
    : _list(std::move(list_in)),
      _strategy_name(std::move(strategy_name_in)),
      _strategy_id(strategy_id_in),
      _priority_strategy(priority_strategy_in),
      _strategy_info(std::move(strategy_info_in)) {
}

FlushStrategyResult::~FlushStrategyResult() = default;

void FlushStrategyResult::drop_non_high_priority_targets() {
    std::vector<std::shared_ptr<FlushContext>> high_pri_list;
    if (_list.front()->getTarget()->getPriority() > IFlushTarget::Priority::NORMAL) {
        high_pri_list.push_back(_list.front());
    }
    std::swap(high_pri_list, _list);
}

} // namespace proton::flushengine
