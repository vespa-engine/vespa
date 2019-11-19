// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "ifetchercallback.h"
#include "ihandle.h"
#include <vespa/config/subscription/configsubscriber.h>
#include <vespa/config/common/timingvalues.h>
#include <vespa/vespalib/util/runnable.h>

namespace config {

/**
 * A config poller runs a polling sequence on a set of configs that it has
 * subscribed to.
 */
class ConfigPoller : public vespalib::Runnable {
public:
    using milliseconds = std::chrono::milliseconds;
    ConfigPoller(const IConfigContext::SP & context);
    ~ConfigPoller();
    void run() override;
    template <typename ConfigType>
    void subscribe(const std::string & configId, IFetcherCallback<ConfigType> * callback, milliseconds subscribeTimeout = DEFAULT_SUBSCRIBE_TIMEOUT);
    void poll();
    void close();
    int64_t getGeneration() const { return _generation; }
private:
    int64_t _generation;
    ConfigSubscriber _subscriber;
    std::vector<IHandle::UP> _handleList;
    std::vector<ICallback *> _callbackList;
};

} // namespace config

#include "configpoller.hpp"
