// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "configsetsourcefactory.h"

namespace config {

ConfigSetSourceFactory::ConfigSetSourceFactory(BuilderMapSP builderMap)
    : _builderMap(std::move(builderMap))
{
}

ConfigSetSourceFactory::~ConfigSetSourceFactory() = default;

std::unique_ptr<Source>
ConfigSetSourceFactory::createSource(std::shared_ptr<IConfigHolder> holder, const ConfigKey & key) const
{
    return std::make_unique<ConfigSetSource>(std::move(holder), key, _builderMap);
}

} // namespace config

