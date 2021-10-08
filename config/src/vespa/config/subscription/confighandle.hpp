// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


namespace config {

template <typename ConfigType>
ConfigHandle<ConfigType>::ConfigHandle(const ConfigSubscription::SP & subscription)
    : _subscription(subscription)
{
}

template <typename ConfigType>
std::unique_ptr<ConfigType>
ConfigHandle<ConfigType>::getConfig() const
{
    return _subscription->getConfig().template newInstance<ConfigType>();
}

template <typename ConfigType>
bool
ConfigHandle<ConfigType>::isChanged() const
{
    return _subscription->isChanged();
}

} // namespace config
