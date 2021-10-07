// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "health_producer.h"
#include <mutex>

namespace vespalib {

class SimpleHealthProducer : public HealthProducer
{
private:
    mutable std::mutex _lock;
    HealthProducer::Health _health;

public:
    SimpleHealthProducer();
    ~SimpleHealthProducer() override;
    void setOk();
    void setFailed(const vespalib::string &msg);
    Health getHealth() const override;
};

} // namespace vespalib

