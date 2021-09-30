// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "servicelayernodecontext.h"

namespace storage {

ServiceLayerNodeContext::ServiceLayerNodeContext(framework::Clock::UP clock, const ContentBucketDbOptions& db_opts)
    : StorageNodeContext(std::make_unique<ServiceLayerComponentRegisterImpl>(db_opts),
                         std::move(clock)),
      _componentRegister(dynamic_cast<ComponentRegister&>(StorageNodeContext::getComponentRegister()))
{
}

} // storage
