// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configretriever.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/config/subscription/sourcespec.h>
#include <cassert>

namespace config {

const vespalib::duration ConfigRetriever::DEFAULT_SUBSCRIBE_TIMEOUT(60s);
const vespalib::duration ConfigRetriever::DEFAULT_NEXTGENERATION_TIMEOUT(60s);

ConfigRetriever::ConfigRetriever(const ConfigKeySet & bootstrapSet,
                                 std::shared_ptr<IConfigContext> context,
                                 vespalib::duration subscribeTimeout)
    : _bootstrapSubscriber(bootstrapSet, context, subscribeTimeout),
      _configSubscriber(),
      _lock(),
      _subscriptionList(),
      _lastKeySet(),
      _context(context),
      _generation(-1),
      _subscribeTimeout(subscribeTimeout),
      _bootstrapRequired(true),
      _closed(false)
{
}

ConfigRetriever::~ConfigRetriever() = default;

ConfigSnapshot
ConfigRetriever::getBootstrapConfigs(vespalib::duration timeout)
{
    bool ret = _bootstrapSubscriber.nextGeneration(timeout);
    if (!ret) {
        return ConfigSnapshot();
    }
    _bootstrapRequired = false;
    return _bootstrapSubscriber.getConfigSnapshot();
}

ConfigSnapshot
ConfigRetriever::getConfigs(const ConfigKeySet & keySet, vespalib::duration timeout)
{
    if (isClosed())
        return ConfigSnapshot();
    if (_bootstrapRequired) {
        throw ConfigRuntimeException("Cannot change keySet until bootstrap getBootstrapConfigs() has been called");
    }
    assert(!keySet.empty());
    if (keySet != _lastKeySet) {
        _lastKeySet = keySet;
        {
            std::lock_guard guard(_lock);
            if (isClosed())
                return ConfigSnapshot();
            _configSubscriber = std::make_unique<GenericConfigSubscriber>(_context);
        }
        _subscriptionList.clear();
        for (const auto & key : keySet) {
            _subscriptionList.push_back(_configSubscriber->subscribe(key, _subscribeTimeout));
        }
    }
    // Try update the subscribers generation if older than bootstrap
    if (_configSubscriber->getGeneration() < _bootstrapSubscriber.getGeneration())
        _configSubscriber->nextGeneration(timeout);

    // If we failed to get a new generation, the user should call us again.
    if (_configSubscriber->getGeneration() < _bootstrapSubscriber.getGeneration()) {
        return ConfigSnapshot();
    }
    // If we are not in sync, even though we got a new generation, we should get
    // another bootstrap.
    _bootstrapRequired = _configSubscriber->getGeneration() > _bootstrapSubscriber.getGeneration();
    if (_bootstrapRequired)
        return ConfigSnapshot();

    _generation = _configSubscriber->getGeneration();
    return ConfigSnapshot(_subscriptionList, _generation);
}

void
ConfigRetriever::close()
{
    std::lock_guard guard(_lock);
    _closed.store(true, std::memory_order_relaxed);
    _bootstrapSubscriber.close();
    if (_configSubscriber)
        _configSubscriber->close();
}

bool
ConfigRetriever::isClosed() const
{
    return _closed.load(std::memory_order_relaxed);
}

}
