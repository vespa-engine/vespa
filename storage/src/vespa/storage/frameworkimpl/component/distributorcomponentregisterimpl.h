// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::DistributorComponentRegisterImpl
 * \ingroup component
 *
 * \brief Subclass of component register impl that handles storage components.
 */
#pragma once

#include <vespa/storage/distributor/bucketdb/mapbucketdatabase.h>
#include <vespa/storage/common/distributorcomponent.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/frameworkimpl/component/storagecomponentregisterimpl.h>

namespace storage {
namespace lib {
    class IdealNodeCalculatorConfigurable;
}

class DistributorComponentRegisterImpl
        : public virtual DistributorComponentRegister,
          public virtual StorageComponentRegisterImpl,
          private StateListener
{
    vespalib::Lock _componentLock;
    std::vector<DistributorManagedComponent*> _components;

    UniqueTimeCalculator* _timeCalculator;
    distributor::MapBucketDatabase _bucketDatabase;
    DistributorConfig _distributorConfig;
    VisitorConfig _visitorConfig;
    lib::ClusterState _clusterState;
    std::unique_ptr<lib::IdealNodeCalculatorConfigurable> _idealNodeCalculator;

public:
    typedef std::unique_ptr<DistributorComponentRegisterImpl> UP;

    DistributorComponentRegisterImpl();

    distributor::BucketDatabase& getBucketDatabase() { return _bucketDatabase; }

    virtual void registerDistributorComponent(DistributorManagedComponent&);

    void setTimeCalculator(UniqueTimeCalculator& calc);
    void setDistributorConfig(const DistributorConfig&);
    void setVisitorConfig(const VisitorConfig&);
    void setDistribution(lib::Distribution::SP);
    void setIdealNodeCalculator(
            std::unique_ptr<lib::IdealNodeCalculatorConfigurable>);

private:
    virtual void handleNewState();

    virtual void setNodeStateUpdater(NodeStateUpdater& updater);
};

} // storage


