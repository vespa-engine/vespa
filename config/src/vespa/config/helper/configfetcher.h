// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configpoller.h"
#include <vespa/config/common/timingvalues.h>
#include <atomic>

namespace vespalib { class Thread; }

namespace config {

/**
 * A config fetcher subscribes to a config and notifies a callback when done
 */
class ConfigFetcher
{
public:
    using milliseconds = std::chrono::milliseconds;
    ConfigFetcher(const IConfigContext::SP & context);
    ConfigFetcher(const SourceSpec & spec = ServerSpec());
    ~ConfigFetcher();

    template <typename ConfigType>
    void subscribe(const std::string & configId, IFetcherCallback<ConfigType> * callback, milliseconds subscribeTimeout = DEFAULT_SUBSCRIBE_TIMEOUT);

    void start();
    void close();
    int64_t getGeneration() const { return _poller.getGeneration(); }
private:
    ConfigPoller _poller;
    std::unique_ptr<vespalib::Thread> _thread;
    std::atomic<bool> _closed;
    std::atomic<bool> _started;
};

} // namespace config


#include "configfetcher.hpp"

