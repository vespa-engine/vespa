// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_health_producer.h"

namespace vespalib {

SimpleHealthProducer::SimpleHealthProducer()
    : _lock(),
      _health(true, "")
{
    setOk();
}

SimpleHealthProducer::~SimpleHealthProducer()
{
}

void
SimpleHealthProducer::setOk()
{
    LockGuard guard(_lock);
    _health = Health(true, "All OK");
}

void
SimpleHealthProducer::setFailed(const vespalib::string &msg)
{
    LockGuard guard(_lock);
    _health = Health(false, msg);
}

HealthProducer::Health
SimpleHealthProducer::getHealth() const
{
    LockGuard guard(_lock);
    return _health;
}

} // namespace vespalib
