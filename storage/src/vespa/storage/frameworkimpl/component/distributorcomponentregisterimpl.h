// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::DistributorComponentRegisterImpl
 * \ingroup component
 *
 * \brief Subclass of component register impl that handles storage components.
 */
#pragma once

#include "storagecomponentregisterimpl.h"
#include <vespa/storage/common/distributorcomponent.h>
#include <vespa/storage/common/nodestateupdater.h>

namespace storage::lib {
    class IdealNodeCalculatorConfigurable;
    class ClusterState;
}

namespace storage {

class DistributorComponentRegisterImpl
        : public virtual DistributorComponentRegister,
          public virtual StorageComponentRegisterImpl,
          private StateListener
{
    std::mutex _componentLock;
    std::vector<DistributorManagedComponent*> _components;

    UniqueTimeCalculator* _timeCalculator;
    DistributorConfig _distributorConfig;
    VisitorConfig _visitorConfig;
    std::shared_ptr<lib::ClusterState> _clusterState;

public:
    using UP = std::unique_ptr<DistributorComponentRegisterImpl>;

    DistributorComponentRegisterImpl();
    ~DistributorComponentRegisterImpl() override;

    void registerDistributorComponent(DistributorManagedComponent&) override;
    void setTimeCalculator(UniqueTimeCalculator& calc);
    void setDistributorConfig(const DistributorConfig&);
    void setVisitorConfig(const VisitorConfig&);
private:
    void handleNewState() noexcept override;
    void setNodeStateUpdater(NodeStateUpdater& updater) override;
};

}
