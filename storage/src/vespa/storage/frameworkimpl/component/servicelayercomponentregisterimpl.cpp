// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "servicelayercomponentregisterimpl.h"
#include <vespa/vespalib/util/exceptions.h>

namespace storage {

using vespalib::IllegalStateException;

ServiceLayerComponentRegisterImpl::ServiceLayerComponentRegisterImpl()
    : _diskCount(0),
      _bucketDatabase()
{ }

void
ServiceLayerComponentRegisterImpl::registerServiceLayerComponent(
        ServiceLayerManagedComponent& smc)
{
    vespalib::LockGuard lock(_componentLock);
    _components.push_back(&smc);
    smc.setDiskCount(_diskCount);
    smc.setBucketDatabase(_bucketDatabase);
    smc.setMinUsedBitsTracker(_minUsedBitsTracker);
}

void
ServiceLayerComponentRegisterImpl::setDiskCount(uint16_t count)
{
    vespalib::LockGuard lock(_componentLock);
    if (_diskCount != 0) {
        throw IllegalStateException("Disk count already set. Cannot be updated live", VESPA_STRLOC);
    }
    _diskCount = count;
    for (uint32_t i=0; i<_components.size(); ++i) {
        _components[i]->setDiskCount(count);
    }
}

} // storage
