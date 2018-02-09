// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::StorageComponentRegisterImpl
 * \ingroup component
 *
 * \brief Subclass of component register impl that handles storage components.
 */
#pragma once

#include "storagecomponentregisterimpl.h"
#include <vespa/storage/bucketdb/minimumusedbitstracker.h>
#include <vespa/storage/common/content_bucket_space_repo.h>
#include <vespa/storage/common/servicelayercomponent.h>

namespace storage {

class ServiceLayerComponentRegisterImpl
        : public virtual ServiceLayerComponentRegister,
          public virtual StorageComponentRegisterImpl
{
    vespalib::Lock _componentLock;
    std::vector<ServiceLayerManagedComponent*> _components;
    uint16_t _diskCount;
    ContentBucketSpaceRepo _bucketSpaceRepo;
    MinimumUsedBitsTracker _minUsedBitsTracker;

public:
    typedef std::unique_ptr<ServiceLayerComponentRegisterImpl> UP;

    ServiceLayerComponentRegisterImpl();

    uint16_t getDiskCount() const { return _diskCount; }
    ContentBucketSpaceRepo& getBucketSpaceRepo() { return _bucketSpaceRepo; }
    MinimumUsedBitsTracker& getMinUsedBitsTracker() {
        return _minUsedBitsTracker;
    }

    void registerServiceLayerComponent(ServiceLayerManagedComponent&) override;
    void setDiskCount(uint16_t count);
    void setDistribution(lib::Distribution::SP distribution) override;
};

} // storage
