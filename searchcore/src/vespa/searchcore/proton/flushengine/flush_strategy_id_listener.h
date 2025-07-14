// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace proton::flushengine {

class FlushStrategyIdNotifier;

/*
 * This abstract class listens to flush strategy id updates from a flush strategy id notifier.
 */
class FlushStrategyIdListener {
protected:
    std::weak_ptr<FlushStrategyIdNotifier> _notifier;
    bool                                   _strategy_id_listener_removed;
public:
    FlushStrategyIdListener(std::shared_ptr<FlushStrategyIdNotifier> notifier);
    virtual ~FlushStrategyIdListener();
    virtual void set_strategy_id(uint32_t strategy_id) = 0;
    virtual void notifier_closed() = 0;
    [[nodiscard]] bool add_to_notifier(std::shared_ptr<FlushStrategyIdListener> self);
    void remove_from_notifier(std::shared_ptr<FlushStrategyIdListener> self);
    [[nodiscard]] bool strategy_id_listener_removed() const noexcept { return _strategy_id_listener_removed; }
    void set_strategy_id_listener_removed() noexcept { _strategy_id_listener_removed = true; }
};

}
