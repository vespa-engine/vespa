// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "simpleconfigretriever.h"

namespace config {
SimpleConfigRetriever::SimpleConfigRetriever(const ConfigKeySet & keySet,
                                             std::shared_ptr<IConfigContext> context,
                                             vespalib::duration subscribeTimeout)
    : _set(context),
      _subscriptionList()
{
    for (const ConfigKey & key : keySet) {
        _subscriptionList.push_back(_set.subscribe(key, subscribeTimeout));
    }
}

ConfigSnapshot
SimpleConfigRetriever::getConfigs(vespalib::duration timeout)
{
    if (_set.acquireSnapshot(timeout, true)) {
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
