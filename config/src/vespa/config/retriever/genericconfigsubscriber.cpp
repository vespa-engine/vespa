// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "genericconfigsubscriber.h"

namespace config {

GenericConfigSubscriber::GenericConfigSubscriber(std::shared_ptr<IConfigContext> context)
    : _set(std::move(context))
{ }

bool
GenericConfigSubscriber::nextGeneration(milliseconds timeoutInMillis)
{
    return _set.acquireSnapshot(timeoutInMillis, true);
}

std::shared_ptr<ConfigSubscription>
GenericConfigSubscriber::subscribe(const ConfigKey & key, milliseconds timeoutInMillis)
{
    return _set.subscribe(key, timeoutInMillis);
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
