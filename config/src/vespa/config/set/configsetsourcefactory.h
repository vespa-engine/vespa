// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configsetsource.h"
#include <vespa/config/common/sourcefactory.h>

namespace config {

class Source;
class IConfigHolder;
class ConfigKey;

/**
 * Factory creating config payload from config instances.
 */
class ConfigSetSourceFactory : public SourceFactory
{
public:
    using BuilderMapSP = ConfigSetSource::BuilderMapSP;
    explicit ConfigSetSourceFactory(BuilderMapSP builderMap);
    ~ConfigSetSourceFactory() override;
    std::unique_ptr<Source> createSource(std::shared_ptr<IConfigHolder>  holder, const ConfigKey & key) const override;
private:
    BuilderMapSP _builderMap;
};

} // namespace config

