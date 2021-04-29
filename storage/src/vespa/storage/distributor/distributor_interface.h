// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace storage { class DistributorConfiguration; }

namespace storage::distributor {

class DistributorMetricSet;

/**
 * Simple interface to access metrics and config for the top-level distributor.
 */
class DistributorInterface {
public:
    virtual ~DistributorInterface() {}
    virtual DistributorMetricSet& metrics() = 0;
    virtual const DistributorConfiguration& config() const = 0;
};

}
