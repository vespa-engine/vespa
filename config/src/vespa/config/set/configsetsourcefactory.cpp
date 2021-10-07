// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "configsetsourcefactory.h"

namespace config {

ConfigSetSourceFactory::ConfigSetSourceFactory(const BuilderMapSP & builderMap)
    : _builderMap(builderMap)
{
}

Source::UP
ConfigSetSourceFactory::createSource(const IConfigHolder::SP & holder, const ConfigKey & key) const
{
    return Source::UP(new ConfigSetSource(holder, key, _builderMap));
}

} // namespace config

