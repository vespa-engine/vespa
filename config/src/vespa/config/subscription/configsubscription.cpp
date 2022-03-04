// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configsubscription.h"
#include <vespa/config/common/configupdate.h>
#include <vespa/config/common/iconfigholder.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/config/common/misc.h>

namespace config {

ConfigSubscription::ConfigSubscription(const SubscriptionId & id, const ConfigKey & key,
                                       std::shared_ptr<IConfigHolder> holder, std::unique_ptr<Source> source)
    : _id(id),
      _key(key),
      _source(std::move(source)),
      _holder(std::move(holder)),
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
ConfigSubscription::nextUpdate(int64_t generation, vespalib::steady_time deadline)
{
    if (_closed || !_holder->poll()) {
        return false;
    }
    auto old = std::move(_next);
    _next = _holder->provide();
    if (old) {
        _next->merge(*old);
    }
    if (isGenerationNewer(_next->getGeneration(), generation)) {
        return true;
    }
    return (!_closed && _holder->wait_until(deadline));
}

bool
ConfigSubscription::hasGenerationChanged() const
{
    return (!_closed && _next && ((_current && (_current->getGeneration() != _next->getGeneration())) || ! _current));
}

bool
ConfigSubscription::hasChanged() const
{
    return (!_closed && _next && ((_next->hasChanged() && _current && (_current->getValue() != _next->getValue())) || ! _current));
}

int64_t
ConfigSubscription::getGeneration() const
{
    return _next->getGeneration();
}

void
ConfigSubscription::close()
{
    if (!_closed.exchange(true)) {
        _holder->close();
        _source->close();
    }
}

void
ConfigSubscription::flip()
{
    bool change = hasChanged();
    if (change) {
        _current = std::move(_next);
        _lastGenerationChanged = _current->getGeneration();
    } else {
        _current = std::make_unique<ConfigUpdate>(_current->getValue(), false, _next->getGeneration());
    }
    _isChanged = change;
}

const ConfigValue &
ConfigSubscription::getConfig() const
{
    if (_closed) {
        throw ConfigRuntimeException("Subscription is closed, config no longer available");
    }
    if ( ! _current) {
        throw ConfigRuntimeException("No configuration available");
    }
    return _current->getValue();
}

void
ConfigSubscription::reload(int64_t generation)
{
    _source->reload(generation);
    _source->getConfig();
}

} // namespace config
