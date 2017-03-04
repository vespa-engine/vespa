// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configpoller.h"

#include <vespa/log/log.h>
LOG_SETUP(".config.helper.configpoller");

namespace config {

ConfigPoller::ConfigPoller(const IConfigContext::SP & context)
    : _generation(-1),
      _subscriber(context),
      _handleList(),
      _callbackList(),
      _genCallback(0)
{
}

ConfigPoller::~ConfigPoller() { }

void
ConfigPoller::run()
{
    while (!_subscriber.isClosed()) {
        try {
            poll();
        } catch (const std::exception & e) {
            LOG(fatal, "Fatal error while configuring: %s", e.what());
        }
    }
}

void
ConfigPoller::poll()
{
    LOG(debug, "Checking for new config");
    if (_subscriber.nextGeneration()) {
        if (_subscriber.isClosed())
            return;
        LOG(debug, "Got new config, reconfiguring");
        _generation = _subscriber.getGeneration();
        for (size_t i = 0; i < _handleList.size(); i++) {
            ICallback * callback(_callbackList[i]);
            if (_handleList[i]->isChanged())
                callback->configure(std::move(_handleList[i]->getConfig()));
        }
        if (_genCallback) {
            _genCallback->notifyGenerationChange(_generation);
        }
    } else {
        LOG(debug, "No new config available");
    }
}

void
ConfigPoller::close()
{
    _subscriber.close();
}

}
