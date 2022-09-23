// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config-slobroks.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/config/subscription/confighandle.h>

namespace config {
    class ConfigSubscriber;
}
namespace slobrok {

class Configurable {
public:
    virtual void setup(const std::vector<std::string> &slobrokSpecs) = 0;
    virtual ~Configurable() = default;
};


class Configurator {
private:
    std::unique_ptr<config::ConfigSubscriber> _subscriber;
    std::unique_ptr<config::ConfigHandle<cloud::config::SlobroksConfig>> _handle;
    Configurable &_target;
public:
    Configurator(Configurable &target, const config::ConfigUri & uri);
    ~Configurator();
    bool poll();
    typedef std::unique_ptr<Configurator> UP;

    int64_t getGeneration() const;
};

class ConfiguratorFactory {
private:
    config::ConfigUri _uri;
public:
    explicit ConfiguratorFactory(const config::ConfigUri & uri);
    // Convenience. Might belong somewhere else
    explicit ConfiguratorFactory(const std::vector<std::string> & spec);

    Configurator::UP create(Configurable &target) const;
};

} // namespace slobrok

