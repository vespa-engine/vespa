// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "simpleconfigretriever.h"
#include "configsnapshot.h"

#include <vespa/vespalib/util/thread.h>
#include <vespa/vespalib/util/runnable.h>

#include <atomic>

namespace config {

class SimpleConfigurable
{
public:
    virtual ~SimpleConfigurable() { }
    virtual void configure(const ConfigSnapshot & snapshot) = 0;
};

/**
 * A SimpleConfigurer runs in its own thread, uses a SimpleConfigRetriever to retrieve configs, and
 * performs a callback whenever a newsnapshot is ready.
 */
class SimpleConfigurer : public vespalib::Runnable
{
public:
    SimpleConfigurer(SimpleConfigRetriever::UP retriever, SimpleConfigurable * const configurable);
    ~SimpleConfigurer();

    /**
     * Start the configurer thread. configure() is guaranteed to be called
     * before this method returns.
     */
    void start();

    /**
     * Close the configurer. This will close the retriever as well!
     */
    void close();

    void run() override;

private:
    void runConfigure();

    SimpleConfigRetriever::UP _retriever;
    SimpleConfigurable * const _configurable;
    std::thread _thread;
    std::atomic<bool> _started;
};

} // namespace config

