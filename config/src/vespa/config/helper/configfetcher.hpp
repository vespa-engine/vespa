// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "configfetcher.h"
#include "configpoller.hpp"

namespace config {

template <typename ConfigType>
void
ConfigFetcher::subscribe(const std::string & configId, IFetcherCallback<ConfigType> * callback, vespalib::duration subscribeTimeout)
{
    _poller->subscribe<ConfigType>(configId, callback, subscribeTimeout);
}

} // namespace config
