// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "servicelayernodecontext.h"
#include <vespa/storageframework/generic/clock/clock.h>

namespace storage {

ServiceLayerNodeContext::ServiceLayerNodeContext(std::unique_ptr<framework::Clock> clock, const ContentBucketDbOptions& db_opts)
    : StorageNodeContext(std::make_unique<ServiceLayerComponentRegisterImpl>(db_opts),
                         std::move(clock)),
      _componentRegister(dynamic_cast<ComponentRegister&>(StorageNodeContext::getComponentRegister()))
{
}

} // storage
