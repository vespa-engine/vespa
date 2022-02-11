// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "cfg.h"
#include <vespa/config/subscription/configsubscriber.hpp>

namespace slobrok {

namespace {

std::vector<std::string>
extract(const cloud::config::SlobroksConfig &cfg)
{
    std::vector<std::string> r;
    for (size_t i = 0; i < cfg.slobrok.size(); ++i) {
        std::string spec(cfg.slobrok[i].connectionspec);
        r.push_back(spec);
    }
    return r;
}

} // namespace <unnamed>

bool
Configurator::poll()
{
    bool retval = _subscriber->nextGenerationNow();
    if (retval) {
        std::unique_ptr<cloud::config::SlobroksConfig> cfg = _handle->getConfig();
        _target.setup(extract(*cfg));
    }
    return retval;
}


Configurator::Configurator(Configurable& target, const config::ConfigUri & uri)
    : _subscriber(std::make_unique<config::ConfigSubscriber>(uri.getContext())),
      _handle(_subscriber->subscribe<cloud::config::SlobroksConfig>(uri.getConfigId())),
      _target(target)
{
}

Configurator::~Configurator() = default;


int64_t
Configurator::getGeneration() const {
    return _subscriber->getGeneration();
}


ConfiguratorFactory::ConfiguratorFactory(const config::ConfigUri& uri)
    : _uri(uri)
{
}

ConfiguratorFactory::ConfiguratorFactory(const std::vector<std::string> & spec)
    : _uri(config::ConfigUri::createEmpty())
{
    cloud::config::SlobroksConfigBuilder builder;
    for (size_t i = 0; i < spec.size(); i++) {
        cloud::config::SlobroksConfig::Slobrok sb;
        sb.connectionspec = spec[i];
        builder.slobrok.push_back(sb);
    }
    _uri = config::ConfigUri::createFromInstance(builder);
}

Configurator::UP
ConfiguratorFactory::create(Configurable& target) const
{
    return std::make_unique<Configurator>(target, _uri);
}

} // namespace slobrok
