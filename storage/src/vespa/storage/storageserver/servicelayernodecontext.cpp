// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "servicelayernodecontext.h"

namespace storage {

ServiceLayerNodeContext::ServiceLayerNodeContext(framework::Clock::UP clock, bool use_btree_db)
    : StorageNodeContext(std::make_unique<ServiceLayerComponentRegisterImpl>(use_btree_db),
                         std::move(clock)),
      _componentRegister(dynamic_cast<ComponentRegister&>(StorageNodeContext::getComponentRegister()))
{
}

} // storage
