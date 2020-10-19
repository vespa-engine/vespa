// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::ServiceLayerComponent
 * \ingroup common
 *
 * \brief Component class including some service layer specific information.
 */

/**
 * \class storage::ServiceLayerComponentRegister
 * \ingroup common
 *
 * \brief Specialization of ComponentRegister handling service layer components.
 */

/**
 * \class storage::ServiceLayerManagedComponent
 * \ingroup common
 *
 * \brief Specialization of StorageManagedComponent.
 *
 * A service layer component register will use this interface in order to set
 * the service layer functionality parts.
 */

#pragma once

#include "storagecomponent.h"
#include <vespa/document/bucket/bucket.h>

namespace storage {

class ContentBucketSpaceRepo;
class MinimumUsedBitsTracker;
class StorBucketDatabase;

struct ServiceLayerManagedComponent
{
    virtual ~ServiceLayerManagedComponent() = default;

    virtual void setBucketSpaceRepo(ContentBucketSpaceRepo&) = 0;
    virtual void setMinUsedBitsTracker(MinimumUsedBitsTracker&) = 0;
};

struct ServiceLayerComponentRegister : public virtual StorageComponentRegister
{
    virtual void registerServiceLayerComponent(ServiceLayerManagedComponent&) = 0;
};

class ServiceLayerComponent : public StorageComponent,
                              private ServiceLayerManagedComponent
{
    ContentBucketSpaceRepo* _bucketSpaceRepo;
    MinimumUsedBitsTracker* _minUsedBitsTracker;

    // ServiceLayerManagedComponent implementation
    void setBucketSpaceRepo(ContentBucketSpaceRepo& repo) override { _bucketSpaceRepo = &repo; }
    void setMinUsedBitsTracker(MinimumUsedBitsTracker& tracker) override {
        _minUsedBitsTracker = &tracker;
    }
public:
    typedef std::unique_ptr<ServiceLayerComponent> UP;

    ServiceLayerComponent(ServiceLayerComponentRegister& compReg,
                          vespalib::stringref name)
        : StorageComponent(compReg, name),
          _bucketSpaceRepo(nullptr),
          _minUsedBitsTracker(nullptr)
    {
        compReg.registerServiceLayerComponent(*this);
    }

    const ContentBucketSpaceRepo &getBucketSpaceRepo() const;
    StorBucketDatabase& getBucketDatabase(document::BucketSpace bucketSpace) const;
    MinimumUsedBitsTracker& getMinUsedBitsTracker() {
        return *_minUsedBitsTracker;
    }
    const MinimumUsedBitsTracker& getMinUsedBitsTracker() const {
        return *_minUsedBitsTracker;
    }
};

} // storage
