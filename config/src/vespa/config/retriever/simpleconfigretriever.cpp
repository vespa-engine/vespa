// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "simpleconfigretriever.h"

namespace config {
SimpleConfigRetriever::SimpleConfigRetriever(const ConfigKeySet & keySet,
                                             const IConfigContext::SP & context,
                                             uint64_t subscribeTimeout)
    : _set(context),
      _subscriptionList()
{
    for (ConfigKeySet::const_iterator it(keySet.begin()), mt(keySet.end()); it != mt; it++) {
        _subscriptionList.push_back(_set.subscribe(*it, subscribeTimeout));
    }
}

ConfigSnapshot
SimpleConfigRetriever::getConfigs(uint64_t timeoutInMillis)
{
    if (_set.acquireSnapshot(timeoutInMillis, true)) {
        return ConfigSnapshot(_subscriptionList, _set.getGeneration());
    }
    return ConfigSnapshot();
}

void
SimpleConfigRetriever::close()
{
    _set.close();
}

bool
SimpleConfigRetriever::isClosed() const
{
    return _set.isClosed();
}

}
