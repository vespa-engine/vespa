// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/ptrholder.h>
#include <vespa/config-slobroks.h>
#include <vespa/config/config.h>

namespace slobrok {

class Configurable {
public:
    virtual void setup(const std::vector<std::string> &slobrokSpecs) = 0;
    virtual ~Configurable() { }
};


class Configurator {
private:
    config::ConfigSubscriber _subscriber;
    config::ConfigHandle<cloud::config::SlobroksConfig>::UP _handle;
    Configurable &_target;
public:
    Configurator(Configurable &target, const config::ConfigUri & uri);
    bool poll();
    typedef std::unique_ptr<Configurator> UP;

    int64_t getGeneration() const { return _subscriber.getGeneration(); }
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

