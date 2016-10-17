// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::DistributorComponent
 * \ingroup common
 *
 * \brief Component class including some service layer specific information.
 */

/**
 * \class storage::DistributorComponentRegister
 * \ingroup common
 *
 * \brief Specialization of ComponentRegister handling service layer components.
 */

/**
 * \class storage::DistributorManagedComponent
 * \ingroup common
 *
 * \brief Specialization of StorageManagedComponent.
 *
 * A service layer component register will use this interface in order to set
 * the service layer functionality parts.
 */

#pragma once

#include <vespa/storageapi/defs.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/storage/config/distributorconfiguration.h>
#include <vespa/storage/config/config-stor-distributormanager.h>
#include <vespa/storage/config/config-stor-visitordispatcher.h>

namespace storage {

namespace bucketdb {
    class DistrBucketDatabase;
}
namespace lib {
    class IdealNodeCalculator;
}

typedef vespa::config::content::core::internal::InternalStorDistributormanagerType DistributorConfig;
typedef vespa::config::content::core::internal::InternalStorVisitordispatcherType VisitorConfig;

struct UniqueTimeCalculator {
    virtual ~UniqueTimeCalculator() {}
    virtual api::Timestamp getUniqueTimestamp() = 0;
};

struct DistributorManagedComponent
{
    virtual ~DistributorManagedComponent() {}

    virtual void setIdealNodeCalculator(lib::IdealNodeCalculator&) = 0;
    virtual void setTimeCalculator(UniqueTimeCalculator&) = 0;
    virtual void setBucketDatabase(BucketDatabase&) = 0;
    virtual void setDistributorConfig(const DistributorConfig&)= 0;
    virtual void setVisitorConfig(const VisitorConfig&) = 0;
};

struct DistributorComponentRegister : public virtual StorageComponentRegister
{
    virtual void registerDistributorComponent(
                    DistributorManagedComponent&) = 0;
};

class DistributorComponent : public StorageComponent,
                             private DistributorManagedComponent
{
    lib::IdealNodeCalculator* _idealNodeCalculator;
    BucketDatabase* _bucketDatabase;
    mutable UniqueTimeCalculator* _timeCalculator;
    DistributorConfig _distributorConfig;
    VisitorConfig _visitorConfig;
    DistributorConfiguration _totalConfig;

        // DistributorManagedComponent implementation
    virtual void setBucketDatabase(BucketDatabase& db)
        { _bucketDatabase = &db; }
    virtual void setIdealNodeCalculator(lib::IdealNodeCalculator& c)
        { _idealNodeCalculator = &c; }
    virtual void setTimeCalculator(UniqueTimeCalculator& utc)
        { _timeCalculator = &utc; }
    virtual void setDistributorConfig(const DistributorConfig& c)
        { _distributorConfig = c; _totalConfig.configure(c); }
    virtual void setVisitorConfig(const VisitorConfig& c)
        { _visitorConfig = c; _totalConfig.configure(c); }

public:
    typedef std::unique_ptr<DistributorComponent> UP;

    DistributorComponent(DistributorComponentRegister& compReg,
                          vespalib::stringref name)
        : StorageComponent(compReg, name),
          _bucketDatabase(0), _timeCalculator(0),
          _totalConfig(*this)
    {
        compReg.registerDistributorComponent(*this);
    }

    api::Timestamp getUniqueTimestamp() const {
        assert(_timeCalculator); return _timeCalculator->getUniqueTimestamp();
    }
    const DistributorConfig& getDistributorConfig() const {
        return _distributorConfig;
    }
    const VisitorConfig& getVisitorConfig() const {
        return _visitorConfig;
    }
    const DistributorConfiguration&
    getTotalDistributorConfig() const {
        return _totalConfig;
    }
    BucketDatabase& getBucketDatabase() {
        assert(_bucketDatabase); return *_bucketDatabase;
    }
    lib::IdealNodeCalculator& getIdealNodeCalculator() const {
        assert(_idealNodeCalculator); return *_idealNodeCalculator;
    }
};

} // storage

