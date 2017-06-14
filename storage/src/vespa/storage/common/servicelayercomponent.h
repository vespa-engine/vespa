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

namespace storage {

class MinimumUsedBitsTracker;
class StorBucketDatabase;

struct ServiceLayerManagedComponent
{
    virtual ~ServiceLayerManagedComponent() {}

    virtual void setDiskCount(uint16_t count) = 0;
    virtual void setBucketDatabase(StorBucketDatabase&) = 0;
    virtual void setMinUsedBitsTracker(MinimumUsedBitsTracker&) = 0;
};

struct ServiceLayerComponentRegister : public virtual StorageComponentRegister
{
    virtual void registerServiceLayerComponent(
                    ServiceLayerManagedComponent&) = 0;
};

class ServiceLayerComponent : public StorageComponent,
                              private ServiceLayerManagedComponent
{
    uint16_t _diskCount;
    StorBucketDatabase* _bucketDatabase;
    MinimumUsedBitsTracker* _minUsedBitsTracker;

    // ServiceLayerManagedComponent implementation
    void setDiskCount(uint16_t count) override { _diskCount = count; }
    void setBucketDatabase(StorBucketDatabase& db) override { _bucketDatabase = &db; }
    void setMinUsedBitsTracker(MinimumUsedBitsTracker& tracker) override {
        _minUsedBitsTracker = &tracker;
    }
public:
    typedef std::unique_ptr<ServiceLayerComponent> UP;

    ServiceLayerComponent(ServiceLayerComponentRegister& compReg,
                          vespalib::stringref name)
        : StorageComponent(compReg, name),
          _diskCount(0),
          _bucketDatabase(0),
          _minUsedBitsTracker(0)
    {
        compReg.registerServiceLayerComponent(*this);
    }

    uint16_t getDiskCount() const { return _diskCount; }
    StorBucketDatabase& getBucketDatabase() const
        { assert(_bucketDatabase != 0); return *_bucketDatabase; }
    MinimumUsedBitsTracker& getMinUsedBitsTracker() {
        assert(_minUsedBitsTracker != 0);
        return *_minUsedBitsTracker;
    }
    const MinimumUsedBitsTracker& getMinUsedBitsTracker() const {
        assert(_minUsedBitsTracker != 0);
        return *_minUsedBitsTracker;
    }
    uint16_t getIdealPartition(const document::BucketId&) const;
    uint16_t getPreferredAvailablePartition(const document::BucketId&) const;
};

} // storage
