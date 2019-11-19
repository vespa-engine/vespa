// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configgetter.h"
#include <vespa/config/subscription/configsubscriber.h>

namespace config {

template <typename ConfigType>
std::unique_ptr<ConfigType>
ConfigGetter<ConfigType>::getConfig(int64_t &generation, const std::string & configId, const SourceSpec & spec)
{
    ConfigSubscriber s(spec);
    std::unique_ptr< ConfigHandle<ConfigType> > h = s.subscribe<ConfigType>(configId);
    s.nextConfigNow();
    generation = s.getGeneration();
    return h->getConfig();
}

template <typename ConfigType>
std::unique_ptr<ConfigType>
ConfigGetter<ConfigType>::getConfig(int64_t &generation, const std::string & configId, const IConfigContext::SP & context, uint64_t subscribeTimeout)
{
    ConfigSubscriber s(context);
    std::unique_ptr< ConfigHandle<ConfigType> > h = s.subscribe<ConfigType>(configId, subscribeTimeout);
    s.nextConfigNow();
    generation = s.getGeneration();
    return h->getConfig();
}

template <typename ConfigType>
std::unique_ptr<ConfigType>
ConfigGetter<ConfigType>::getConfig(const std::string & configId, const SourceSpec & spec)
{
    int64_t ignoreGeneration;
    return getConfig(ignoreGeneration, configId, spec);
}

template <typename ConfigType>
std::unique_ptr<ConfigType>
ConfigGetter<ConfigType>::getConfig(const std::string & configId, const IConfigContext::SP & context, uint64_t subscribeTimeout)
{
    int64_t ignoreGeneration;
    return getConfig(ignoreGeneration, configId, context, subscribeTimeout);
}

} // namespace config
