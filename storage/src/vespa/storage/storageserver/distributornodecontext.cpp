// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributornodecontext.h"

namespace storage {

DistributorNodeContext::DistributorNodeContext(
        framework::Clock::UP clock)
    : StorageNodeContext(StorageComponentRegisterImpl::UP(new DistributorComponentRegisterImpl),
                         std::move(clock)),
      _componentRegister(dynamic_cast<ComponentRegister&>(StorageNodeContext::getComponentRegister()))
{
}

} // storage
