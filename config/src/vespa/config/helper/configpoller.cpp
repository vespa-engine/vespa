// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configpoller.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/config/subscription/configsubscriber.h>

#include <vespa/log/log.h>
LOG_SETUP(".config.helper.configpoller");

namespace config {

ConfigPoller::ConfigPoller(std::shared_ptr<IConfigContext> context)
    : _generation(-1),
      _subscriber(std::make_unique<ConfigSubscriber>(std::move(context))),
      _handleList(),
      _callbackList()
{
}

ConfigPoller::~ConfigPoller() = default;

void
ConfigPoller::run()
{
    try {
        while (!_subscriber->isClosed()) {
            poll();
        }
    } catch (config::InvalidConfigException & e) {
        LOG(fatal, "Got exception, will just exit quickly : %s", e.what());
        std::_Exit(17);
    }
}

void
ConfigPoller::poll()
{
    LOG(debug, "Checking for new config");
    if (_subscriber->nextGeneration()) {
        if (_subscriber->isClosed())
            return;
        LOG(debug, "Got new config, reconfiguring");
        _generation = _subscriber->getGeneration();
        for (size_t i = 0; i < _handleList.size(); i++) {
            ICallback * callback(_callbackList[i]);
            if (_handleList[i]->isChanged())
                callback->configure(_handleList[i]->getConfig());
        }
    } else {
        LOG(debug, "No new config available");
    }
}

void
ConfigPoller::close()
{
    _subscriber->close();
}

}
