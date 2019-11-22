// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configretriever.h"
#include <vespa/config/common/exceptions.h>

using std::chrono::milliseconds;

namespace config {

 const milliseconds ConfigRetriever::DEFAULT_SUBSCRIBE_TIMEOUT(60000);
 const milliseconds ConfigRetriever::DEFAULT_NEXTGENERATION_TIMEOUT(60000);

ConfigRetriever::ConfigRetriever(const ConfigKeySet & bootstrapSet,
                                 const IConfigContext::SP & context,
                                 milliseconds subscribeTimeout)
    : _bootstrapSubscriber(bootstrapSet, context, subscribeTimeout),
      _configSubscriber(),
      _lock(),
      _subscriptionList(),
      _lastKeySet(),
      _context(context),
      _closed(false),
      _generation(-1),
      _subscribeTimeout(subscribeTimeout),
      _bootstrapRequired(true)
{
}

ConfigRetriever::~ConfigRetriever() = default;

ConfigSnapshot
ConfigRetriever::getBootstrapConfigs(milliseconds timeoutInMillis)
{
    bool ret = _bootstrapSubscriber.nextGeneration(timeoutInMillis);
    if (!ret) {
        return ConfigSnapshot();
    }
    _bootstrapRequired = false;
    return _bootstrapSubscriber.getConfigSnapshot();
}

ConfigSnapshot
ConfigRetriever::getConfigs(const ConfigKeySet & keySet, milliseconds timeoutInMillis)
{
    if (_closed)
        return ConfigSnapshot();
    if (_bootstrapRequired) {
        throw ConfigRuntimeException("Cannot change keySet until bootstrap getBootstrapConfigs() has been called");
    }
    assert(!keySet.empty());
    if (keySet != _lastKeySet) {
        _lastKeySet = keySet;
        {
            vespalib::LockGuard guard(_lock);
            if (_closed)
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
        _configSubscriber->nextGeneration(timeoutInMillis);

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
    vespalib::LockGuard guard(_lock);
    _closed = true;
    _bootstrapSubscriber.close();
    if (_configSubscriber)
        _configSubscriber->close();
}

bool
ConfigRetriever::isClosed() const
{
    return (_closed);
}

}
