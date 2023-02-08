// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributornodecontext.h"
#include <vespa/storageframework/generic/clock/clock.h>

namespace storage {

DistributorNodeContext::DistributorNodeContext(std::unique_ptr<framework::Clock> clock)
    : StorageNodeContext(std::make_unique<DistributorComponentRegisterImpl>(), std::move(clock)),
      _componentRegister(dynamic_cast<ComponentRegister&>(StorageNodeContext::getComponentRegister()))
{
}

} // storage
