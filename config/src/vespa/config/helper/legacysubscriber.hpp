// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

namespace config {

template <typename ConfigType>
void
LegacySubscriber::subscribe(const std::string & configId, IFetcherCallback<ConfigType> * callback)
{
    if (isLegacyConfigId(configId)) {
        std::string legacyId(legacyConfigId2ConfigId(configId));
        std::unique_ptr<SourceSpec> spec(legacyConfigId2Spec(configId));
        _fetcher.reset(new ConfigFetcher(IConfigContext::SP(new ConfigContext(*spec))));
        _fetcher->subscribe<ConfigType>(legacyId, callback);
    } else {
        _fetcher.reset(new ConfigFetcher());
        _fetcher->subscribe<ConfigType>(configId, callback);
    }
    _configId = configId;
    _fetcher->start();
}

} // namespace config
