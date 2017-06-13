// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "servicelayernodecontext.h"

namespace storage {

ServiceLayerNodeContext::ServiceLayerNodeContext(
        framework::Clock::UP clock)
    : StorageNodeContext(StorageComponentRegisterImpl::UP(new ServiceLayerComponentRegisterImpl),
                         std::move(clock)),
      _componentRegister(dynamic_cast<ComponentRegister&>(StorageNodeContext::getComponentRegister()))
{
}

} // storage
