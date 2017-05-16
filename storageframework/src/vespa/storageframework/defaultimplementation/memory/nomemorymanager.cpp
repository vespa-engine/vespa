// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nomemorymanager.h"
#include <vespa/vespalib/util/exceptions.h>

namespace storage::framework::defaultimplementation {

NoMemoryManager::~NoMemoryManager() {}

const MemoryAllocationType&
NoMemoryManager::registerAllocationType(const MemoryAllocationType& type)
{
    vespalib::LockGuard lock(_typeLock);
    _types[type.getName()] = MemoryAllocationType::UP(
                                new MemoryAllocationType(type));
    return *_types[type.getName()];
}

const MemoryAllocationType&
NoMemoryManager::getAllocationType(const std::string& name) const
{
    vespalib::LockGuard lock(_typeLock);
    std::map<std::string, MemoryAllocationType::UP>::const_iterator it(
            _types.find(name));
    if (it == _types.end()) {
        throw vespalib::IllegalArgumentException(
                "Allocation type not found: " + name, VESPA_STRLOC);
    }
    return *it->second;
}

std::vector<const MemoryAllocationType*>
NoMemoryManager::getAllocationTypes() const
{
    vespalib::LockGuard lock(_typeLock);
    std::vector<const MemoryAllocationType*> types;
    for(std::map<std::string, MemoryAllocationType::UP>::const_iterator it
            = _types.begin(); it != _types.end(); ++it)
    {
        types.push_back(it->second.get());
    }
    return types;
}

}

