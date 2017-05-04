// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configpoller.h"
#include <vespa/config/config.h>
#include <vespa/config/common/timingvalues.h>
#include <vespa/vespalib/util/thread.h>
#include <atomic>

namespace config {

/**
 * A config fetcher subscribes to a config and notifies a callback when done
 */
class ConfigFetcher
{
public:
    ConfigFetcher(const IConfigContext::SP & context);
    ConfigFetcher(const SourceSpec & spec = ServerSpec());
    ~ConfigFetcher();

    template <typename ConfigType>
    void subscribe(const std::string & configId, IFetcherCallback<ConfigType> * callback, uint64_t subscribeTimeout = DEFAULT_SUBSCRIBE_TIMEOUT);

    void subscribeGenerationChanges(IGenerationCallback * callback) {
        _poller.subscribeGenerationChanges(callback);
    }

    void start();
    void close();
    int64_t getGeneration() const { return _poller.getGeneration(); }
private:
    ConfigPoller _poller;
    vespalib::Thread _thread;
    std::atomic<bool> _closed;
    std::atomic<bool> _started;
};

} // namespace config


#include "configfetcher.hpp"

