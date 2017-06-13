// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/common/exceptions.h>
#include <vespa/config/common/misc.h>
#include "configsubscription.h"

namespace config {

ConfigSubscription::ConfigSubscription(const SubscriptionId & id, const ConfigKey & key, const IConfigHolder::SP & holder, Source::UP source)
    : _id(id),
      _key(key),
      _source(std::move(source)),
      _holder(holder),
      _next(),
      _current(),
      _isChanged(false),
      _lastGenerationChanged(-1),
      _closed(false)
{
}

ConfigSubscription::~ConfigSubscription()
{
    close();
}


bool
ConfigSubscription::nextUpdate(int64_t generation, uint64_t timeoutInMillis)
{
    if (_closed || !_holder->poll())
        return false;
    _next.reset(_holder->provide().release());
    if (isGenerationNewer(_next->getGeneration(), generation)) {
        return true;
    }
    return (!_closed && _holder->wait(timeoutInMillis));
}

bool
ConfigSubscription::hasChanged() const
{
    return (!_closed && (_next->hasChanged() || _current.get() == NULL));
}

int64_t
ConfigSubscription::getGeneration() const
{
    return _next->getGeneration();
}

const ConfigKey &
ConfigSubscription::getKey() const
{
    return _key;
}

void
ConfigSubscription::close()
{
    if (!_closed) {
        _closed = true;
        _holder->interrupt();
        _source->close();
    }
}

void
ConfigSubscription::reset()
{
    _isChanged = false;
}

bool
ConfigSubscription::isChanged() const
{
    return _isChanged;
}

int64_t
ConfigSubscription::getLastGenerationChanged() const
{
    return _lastGenerationChanged;
}

void
ConfigSubscription::flip()
{
    bool change = hasChanged();
    if (change) {
        _current.reset(_next.release());
        _lastGenerationChanged = _current->getGeneration();
    } else {
        _current.reset(new ConfigUpdate(_current->getValue(), false, _next->getGeneration()));
    }
    _isChanged = change;
}

ConfigValue
ConfigSubscription::getConfig() const
{
    if (_closed)
        throw ConfigRuntimeException("Subscription is closed, config no longer available");
    if (_current.get() == NULL)
        throw ConfigRuntimeException("No configuration available");
    return _current->getValue();
}

void
ConfigSubscription::reload(int64_t generation)
{
    _source->reload(generation);
    _source->getConfig();
}

} // namespace config
