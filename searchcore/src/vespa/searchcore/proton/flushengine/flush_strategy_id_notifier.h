// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <condition_variable>
#include <memory>
#include <mutex>
#include <vector>

namespace proton::flushengine {

class FlushStrategyIdListener;

/*
 * Class used to notify when strategy_id increases.
 */
class FlushStrategyIdNotifier {
    using Listeners = std::vector<std::shared_ptr<FlushStrategyIdListener>>;
    std::mutex              _lock;
    std::condition_variable _cond;
    uint32_t                _strategy_id;
    bool                    _closed;
    Listeners               _listeners;
public:
    FlushStrategyIdNotifier(uint32_t strategy_id);
    ~FlushStrategyIdNotifier();
    void set_strategy_id(uint32_t strategy_id);
    void close();
    void wait_ge_strategy_id(uint32_t strategy_id);
    [[nodiscard]] bool add_strategy_id_listener(std::shared_ptr<FlushStrategyIdListener> listener);
    void remove_strategy_id_listener(std::shared_ptr<FlushStrategyIdListener> listener);
};

}
