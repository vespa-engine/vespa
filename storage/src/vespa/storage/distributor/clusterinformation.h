// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <stdint.h>
#include <vector>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/stllike/string.h>

namespace storage {

namespace lib {

class ClusterStateBundle;

}

namespace distributor {

class ClusterInformation
{
public:
    typedef std::shared_ptr<const ClusterInformation> CSP;

    virtual ~ClusterInformation() {}

    virtual uint16_t getDistributorIndex() const = 0;

    virtual const lib::ClusterStateBundle& getClusterStateBundle() const = 0;

    virtual const char* getStorageUpStates() const = 0;

    uint16_t getStorageNodeCount() const;
};

}
}

