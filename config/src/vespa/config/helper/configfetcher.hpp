// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

namespace config {

template <typename ConfigType>
void
ConfigFetcher::subscribe(const std::string & configId, IFetcherCallback<ConfigType> * callback, milliseconds subscribeTimeout)
{
    _poller.subscribe<ConfigType>(configId, callback, subscribeTimeout);
}

} // namespace config
