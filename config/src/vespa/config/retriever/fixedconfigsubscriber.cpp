// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "fixedconfigsubscriber.h"

namespace config {
FixedConfigSubscriber::FixedConfigSubscriber(const ConfigKeySet & keySet,
                                             const IConfigContext::SP & context,
                                             milliseconds subscribeTimeout)
    : _set(context),
      _subscriptionList()
{
    for (const ConfigKey & key : keySet) {
        _subscriptionList.push_back(_set.subscribe(key, subscribeTimeout));
    }
}

bool
FixedConfigSubscriber::nextGeneration(milliseconds timeoutInMillis)
{
    return _set.acquireSnapshot(timeoutInMillis, true);
}

void
FixedConfigSubscriber::close()
{
    _set.close();
}

int64_t
FixedConfigSubscriber::getGeneration() const
{
    return _set.getGeneration();
}

ConfigSnapshot
FixedConfigSubscriber::getConfigSnapshot() const
{
    return ConfigSnapshot(_subscriptionList, _set.getGeneration());
}

}
