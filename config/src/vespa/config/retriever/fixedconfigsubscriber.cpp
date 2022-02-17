// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "fixedconfigsubscriber.h"

namespace config {
FixedConfigSubscriber::FixedConfigSubscriber(const ConfigKeySet & keySet,
                                             std::shared_ptr<IConfigContext> context,
                                             vespalib::duration subscribeTimeout)
    : _set(std::move(context)),
      _subscriptionList()
{
    for (const ConfigKey & key : keySet) {
        _subscriptionList.push_back(_set.subscribe(key, subscribeTimeout));
    }
}

bool
FixedConfigSubscriber::nextGeneration(vespalib::duration timeout)
{
    return _set.acquireSnapshot(timeout, true);
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
