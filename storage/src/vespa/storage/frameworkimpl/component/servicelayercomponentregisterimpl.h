// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::StorageComponentRegisterImpl
 * \ingroup component
 *
 * \brief Subclass of component register impl that handles storage components.
 */
#pragma once

#include <vespa/storage/bucketdb/minimumusedbitstracker.h>
#include <vespa/storage/bucketdb/storbucketdb.h>
#include <vespa/storage/common/servicelayercomponent.h>
#include <vespa/storage/frameworkimpl/component/storagecomponentregisterimpl.h>

namespace storage {

class ServiceLayerComponentRegisterImpl
        : public virtual ServiceLayerComponentRegister,
          public virtual StorageComponentRegisterImpl
{
    vespalib::Lock _componentLock;
    std::vector<ServiceLayerManagedComponent*> _components;
    uint16_t _diskCount;
    StorBucketDatabase _bucketDatabase;
    MinimumUsedBitsTracker _minUsedBitsTracker;

public:
    typedef std::unique_ptr<ServiceLayerComponentRegisterImpl> UP;

    ServiceLayerComponentRegisterImpl();

    uint16_t getDiskCount() const { return _diskCount; }
    StorBucketDatabase& getBucketDatabase() { return _bucketDatabase; }
    MinimumUsedBitsTracker& getMinUsedBitsTracker() {
        return _minUsedBitsTracker;
    }

    virtual void registerServiceLayerComponent(ServiceLayerManagedComponent&) override;

    void setDiskCount(uint16_t count);
};

} // storage


