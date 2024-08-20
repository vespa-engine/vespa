// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "component_config_producer.h"
#include <map>
#include <mutex>

namespace vespalib {

class SimpleComponentConfigProducer : public ComponentConfigProducer
{
private:
    std::mutex _lock;
    std::map<std::string, Config> _state;

public:
    SimpleComponentConfigProducer();
    ~SimpleComponentConfigProducer() override;
    void addConfig(const Config &config);
    void removeConfig(const std::string &name);
    void getComponentConfig(Consumer &consumer) override;
};

} // namespace vespalib

