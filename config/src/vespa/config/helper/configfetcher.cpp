// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configfetcher.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/config/common/configcontext.h>
#include <vespa/vespalib/util/thread.h>
#include <vespa/log/log.h>
LOG_SETUP(".config.helper.configfetcher");

namespace config {

ConfigFetcher::ConfigFetcher(const IConfigContext::SP & context)
    : _poller(context),
      _thread(std::make_unique<vespalib::Thread>(_poller)),
      _closed(false),
      _started(false)
{
}

ConfigFetcher::ConfigFetcher(const SourceSpec & spec)
    : _poller(std::make_shared<ConfigContext>(spec)),
      _thread(std::make_unique<vespalib::Thread>(_poller)),
      _closed(false),
      _started(false)
{
}

void
ConfigFetcher::start()
{
    if (!_closed) {
        LOG(debug, "Polling for config");
        _poller.poll();
        if (_poller.getGeneration() == -1) {
            throw ConfigTimeoutException("ConfigFetcher::start timed out getting initial config");
        }
        LOG(debug, "Starting fetcher thread...");
        _thread->start();
        _started = true;
        LOG(debug, "Fetcher thread started");
    }
}

ConfigFetcher::~ConfigFetcher()
{
    close();
}

void
ConfigFetcher::close()
{
    if (!_closed) {
        _poller.close();
        if (_started)
            _thread->join();
    }
}

} // namespace config
