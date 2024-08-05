// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "servicelayercomponentregisterimpl.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/vdslib/distribution/global_bucket_space_distribution_converter.h>

namespace storage {

using vespalib::IllegalStateException;

ServiceLayerComponentRegisterImpl::ServiceLayerComponentRegisterImpl(const ContentBucketDbOptions& db_opts)
    : _bucketSpaceRepo(db_opts)
{ }

void
ServiceLayerComponentRegisterImpl::registerServiceLayerComponent(ServiceLayerManagedComponent& smc)
{
    std::lock_guard lock(_componentLock);
    _components.push_back(&smc);
    smc.setBucketSpaceRepo(_bucketSpaceRepo);
    smc.setMinUsedBitsTracker(_minUsedBitsTracker);
}

void
ServiceLayerComponentRegisterImpl::setDistribution(std::shared_ptr<const lib::Distribution> distribution)
{
    // TODO remove this override entirely?
    StorageComponentRegisterImpl::setDistribution(distribution);
}

} // storage
