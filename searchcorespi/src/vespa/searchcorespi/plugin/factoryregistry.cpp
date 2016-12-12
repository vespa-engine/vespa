// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcorespi/plugin/factoryregistry.h>
#include <vespa/vespalib/util/exceptions.h>

using vespalib::LockGuard;
using vespalib::IllegalArgumentException;
using vespalib::stringref;
using vespalib::string;

namespace searchcorespi {

FactoryRegistry::FactoryRegistry()
{
}

FactoryRegistry::~FactoryRegistry()
{
}

void FactoryRegistry::add(const stringref & uniqueName, const IIndexManagerFactory::SP & factory)
{
    LockGuard guard(_lock);
    if (_registry.find(uniqueName) == _registry.end()) {
        _registry[uniqueName] = factory;
    } else {
        throw IllegalArgumentException("A factory is already registered with the same name as '" + uniqueName + "'.", VESPA_STRLOC);
    }
}

void FactoryRegistry::remove(const stringref & uniqueName)
{
    LockGuard guard(_lock);
    if (_registry.find(uniqueName) == _registry.end()) {
        throw IllegalArgumentException("No factory is registered with the name of '" + uniqueName + "'.", VESPA_STRLOC);
    }
    _registry.erase(uniqueName);
}

const IIndexManagerFactory::SP &
FactoryRegistry::get(const stringref & uniqueName) const
{
    LockGuard guard(_lock);
    Registry::const_iterator found = _registry.find(uniqueName);
    if (found == _registry.end()) {
        throw IllegalArgumentException("No factory is registered with the name of '" + uniqueName + "'.", VESPA_STRLOC);
    }
    return found->second;
}

bool
FactoryRegistry::isRegistered(const vespalib::stringref & uniqueName) const
{
    LockGuard guard(_lock);
    Registry::const_iterator found = _registry.find(uniqueName);
    return found != _registry.end();
}

}
