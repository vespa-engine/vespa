// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "servicelayercomponent.h"

#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/vdslib/distribution/distribution.h>

namespace storage {

uint16_t
ServiceLayerComponent::getIdealPartition(const document::BucketId& bucket) const
{
    return getDistribution()->getIdealDisk(
            *getStateUpdater().getReportedNodeState(), getIndex(), bucket,
            lib::Distribution::IDEAL_DISK_EVEN_IF_DOWN);
}

uint16_t
ServiceLayerComponent::getPreferredAvailablePartition(
        const document::BucketId& bucket) const
{
    return getDistribution()->getPreferredAvailableDisk(
            *getStateUpdater().getReportedNodeState(), getIndex(), bucket);
}

} // storage
