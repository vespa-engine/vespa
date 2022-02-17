// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configgetter.h"
#include <vespa/config/subscription/configsubscriber.hpp>

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
ConfigGetter<ConfigType>::getConfig(int64_t &generation, const std::string & configId, std::shared_ptr<IConfigContext> context, vespalib::duration subscribeTimeout)
{
    ConfigSubscriber s(std::move(context));
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
ConfigGetter<ConfigType>::getConfig(const std::string & configId, std::shared_ptr<IConfigContext> context, vespalib::duration subscribeTimeout)
{
    int64_t ignoreGeneration;
    return getConfig(ignoreGeneration, configId, std::move(context), subscribeTimeout);
}

} // namespace config
