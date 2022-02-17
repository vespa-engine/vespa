// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "legacysubscriber.h"
#include "configfetcher.hpp"
#include <vespa/config/common/configcontext.h>

namespace config {

template <typename ConfigType>
void
LegacySubscriber::subscribe(const std::string & configId, IFetcherCallback<ConfigType> * callback)
{
    if (isLegacyConfigId(configId)) {
        std::string legacyId(legacyConfigId2ConfigId(configId));
        std::unique_ptr<SourceSpec> spec(legacyConfigId2Spec(configId));
        _fetcher = std::make_unique<ConfigFetcher>(std::make_shared<ConfigContext>(*spec));
        _fetcher->subscribe<ConfigType>(legacyId, callback);
    } else {
        _fetcher = std::make_unique<ConfigFetcher>();
        _fetcher->subscribe<ConfigType>(configId, callback);
    }
    _configId = configId;
    _fetcher->start();
}

} // namespace config
