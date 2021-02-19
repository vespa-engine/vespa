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
    std::mutex _componentLock;
    std::vector<ServiceLayerManagedComponent*> _components;
    ContentBucketSpaceRepo _bucketSpaceRepo;
    MinimumUsedBitsTracker _minUsedBitsTracker;

public:
    typedef std::unique_ptr<ServiceLayerComponentRegisterImpl> UP;

    explicit ServiceLayerComponentRegisterImpl(const ContentBucketDbOptions&);

    ContentBucketSpaceRepo& getBucketSpaceRepo() { return _bucketSpaceRepo; }
    MinimumUsedBitsTracker& getMinUsedBitsTracker() {
        return _minUsedBitsTracker;
    }

    void registerServiceLayerComponent(ServiceLayerManagedComponent&) override;
    void setDistribution(std::shared_ptr<lib::Distribution> distribution) override;
};

} // storage
