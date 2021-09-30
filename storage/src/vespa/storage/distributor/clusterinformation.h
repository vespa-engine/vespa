// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/stllike/string.h>
#include <cstdint>
#include <vector>
#include <memory>

namespace storage::lib { class ClusterStateBundle; }

namespace storage::distributor {

class ClusterInformation
{
public:
    using CSP = std::shared_ptr<const ClusterInformation>;

    virtual ~ClusterInformation() = default;
    virtual uint16_t getDistributorIndex() const = 0;
    virtual const lib::ClusterStateBundle& getClusterStateBundle() const = 0;
    virtual const char* getStorageUpStates() const = 0;
    uint16_t getStorageNodeCount() const;
};

}
