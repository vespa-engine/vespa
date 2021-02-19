// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "servicelayercomponentregisterimpl.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/common/global_bucket_space_distribution_converter.h>

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
ServiceLayerComponentRegisterImpl::setDistribution(std::shared_ptr<lib::Distribution> distribution)
{
    _bucketSpaceRepo.get(document::FixedBucketSpaces::default_space()).setDistribution(distribution);
    auto global_distr = GlobalBucketSpaceDistributionConverter::convert_to_global(*distribution);
    _bucketSpaceRepo.get(document::FixedBucketSpaces::global_space()).setDistribution(global_distr);
    StorageComponentRegisterImpl::setDistribution(distribution);
}

} // storage
