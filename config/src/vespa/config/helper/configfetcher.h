// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/timingvalues.h>
#include <vespa/config/helper/ifetchercallback.h>
#include <vespa/config/subscription/sourcespec.h>
#include <atomic>

namespace vespalib { class Thread; }

namespace config {

class ConfigPoller;
class IConfigContext;

/**
 * A config fetcher subscribes to a config and notifies a callback when done
 */
class ConfigFetcher
{
public:
    ConfigFetcher(std::shared_ptr<IConfigContext> context);
    ConfigFetcher(const SourceSpec & spec = ServerSpec());
    ~ConfigFetcher();

    template <typename ConfigType>
    void subscribe(const std::string & configId, IFetcherCallback<ConfigType> * callback, vespalib::duration subscribeTimeout = DEFAULT_SUBSCRIBE_TIMEOUT);

    void start();
    void close();
    int64_t getGeneration() const;
private:
    std::unique_ptr<ConfigPoller> _poller;
    std::unique_ptr<vespalib::Thread> _thread;
    std::atomic<bool> _closed;
    std::atomic<bool> _started;
};

} // namespace config
