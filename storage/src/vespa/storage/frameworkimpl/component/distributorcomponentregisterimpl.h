// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::DistributorComponentRegisterImpl
 * \ingroup component
 *
 * \brief Subclass of component register impl that handles storage components.
 */
#pragma once

#include <vespa/storage/bucketdb/mapbucketdatabase.h>
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
    DistributorConfig _distributorConfig;
    VisitorConfig _visitorConfig;
    lib::ClusterState _clusterState;

public:
    typedef std::unique_ptr<DistributorComponentRegisterImpl> UP;

    DistributorComponentRegisterImpl();
    ~DistributorComponentRegisterImpl();

    virtual void registerDistributorComponent(DistributorManagedComponent&) override;

    void setTimeCalculator(UniqueTimeCalculator& calc);
    void setDistributorConfig(const DistributorConfig&);
    void setVisitorConfig(const VisitorConfig&);

private:
    virtual void handleNewState() override;

    virtual void setNodeStateUpdater(NodeStateUpdater& updater) override;
};

} // storage


