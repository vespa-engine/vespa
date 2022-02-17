// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "genericconfigsubscriber.h"

namespace config {

GenericConfigSubscriber::GenericConfigSubscriber(std::shared_ptr<IConfigContext> context)
    : _set(std::move(context))
{ }

bool
GenericConfigSubscriber::nextGeneration(vespalib::duration timeout)
{
    return _set.acquireSnapshot(timeout, true);
}

std::shared_ptr<ConfigSubscription>
GenericConfigSubscriber::subscribe(const ConfigKey & key, vespalib::duration timeout)
{
    return _set.subscribe(key, timeout);
}

void
GenericConfigSubscriber::close()
{
    _set.close();
}

int64_t
GenericConfigSubscriber::getGeneration() const
{
    return _set.getGeneration();
}

}
