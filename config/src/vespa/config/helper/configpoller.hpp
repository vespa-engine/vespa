// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

namespace config {

template <typename ConfigType>
void
ConfigPoller::subscribe(const std::string & configId, IFetcherCallback<ConfigType> * callback, milliseconds subscribeTimeout)
{
    std::unique_ptr<ConfigHandle<ConfigType> > handle(_subscriber.subscribe<ConfigType>(configId, subscribeTimeout));
    _handleList.emplace_back(std::make_unique<GenericHandle<ConfigType>>(std::move(handle)));
    _callbackList.push_back(callback);
}

} // namespace config
