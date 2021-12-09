// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simpleconfigurer.h"
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".config.retriever.simpleconfigurer");

namespace config {

VESPA_THREAD_STACK_TAG(simple_configurer_thread);

SimpleConfigurer::SimpleConfigurer(SimpleConfigRetriever::UP retriever, SimpleConfigurable * const configurable)
    : _retriever(std::move(retriever)),
      _configurable(configurable),
      _thread(*this, simple_configurer_thread),
      _started(false)
{
    assert(_retriever);
}

void
SimpleConfigurer::start()
{
    if (!_retriever->isClosed()) {
        LOG(debug, "Polling for config");
        runConfigure();
        _thread.start();
        _started = true;
    }
}

SimpleConfigurer::~SimpleConfigurer()
{
    close();
}

void
SimpleConfigurer::close()
{
    _retriever->close();
    if (_started)
        _thread.join();
}

void
SimpleConfigurer::runConfigure()
{
    ConfigSnapshot snapshot(_retriever->getConfigs());
    if (!snapshot.empty()) {
        _configurable->configure(snapshot);
    }
}

void
SimpleConfigurer::run()
{
    while (!_retriever->isClosed()) {
        try {
            runConfigure();
        } catch (const std::exception & e) {
            LOG(fatal, "Fatal error while configuring: %s", e.what());
        }
    }
}

} // namespace config
