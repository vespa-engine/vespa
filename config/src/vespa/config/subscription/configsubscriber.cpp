// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configsubscriber.h"

namespace config {


ConfigSubscriber::ConfigSubscriber(const IConfigContext::SP & context)
    : _set(context)

{ }

ConfigSubscriber::ConfigSubscriber(const SourceSpec & spec)
    : _set(std::make_shared<ConfigContext>(spec))
{ }

bool
ConfigSubscriber::nextConfig(milliseconds timeoutInMillis)
{
    return _set.acquireSnapshot(timeoutInMillis, false);
}

bool
ConfigSubscriber::nextGeneration(milliseconds timeoutInMillis)
{
    return _set.acquireSnapshot(timeoutInMillis, true);
}

void
ConfigSubscriber::close()
{
    _set.close();
}

bool
ConfigSubscriber::isClosed() const
{
    return _set.isClosed();
}

int64_t
ConfigSubscriber::getGeneration() const
{
    return _set.getGeneration();
}

}
