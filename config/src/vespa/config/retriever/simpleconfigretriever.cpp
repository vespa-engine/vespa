// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "simpleconfigretriever.h"

namespace config {
SimpleConfigRetriever::SimpleConfigRetriever(const ConfigKeySet & keySet,
                                             const IConfigContext::SP & context,
                                             milliseconds subscribeTimeout)
    : _set(context),
      _subscriptionList()
{
    for (const ConfigKey & key : keySet) {
        _subscriptionList.push_back(_set.subscribe(key, subscribeTimeout));
    }
}

ConfigSnapshot
SimpleConfigRetriever::getConfigs(milliseconds timeoutInMillis)
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
