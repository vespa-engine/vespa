// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "distributormessagesender.h"

namespace storage { class DistributorConfiguration; }

namespace storage::distributor {

class DistributorMetricSet;

/**
 * Simple interface to access metrics and config for the top-level distributor.
 */
class DistributorInterface : public DistributorMessageSender {
public:
    ~DistributorInterface() override = default;
    [[nodiscard]] virtual DistributorMetricSet& metrics() = 0;
    [[nodiscard]] virtual const DistributorConfiguration& config() const = 0;
    // Called from our own bucket DB updater when a cluster state bundle with embedded distribution
    // config is received. Once at least one such embedded config has been received, config from
    // the storage component should be _ignored_, as the cluster controller is the lone source of
    // truth for distribution config.
    // Returns true iff `distribution` differs from the existing config.
    [[nodiscard]] virtual bool receive_distribution_from_cluster_controller(std::shared_ptr<const lib::Distribution> distribution) = 0;

    // Whether this distributor treats the CC as the source of truth for distribution config, and
    // thus ignores node-internal distribution config changes.
    [[nodiscard]] virtual bool cluster_controller_is_distribution_source_of_truth() const noexcept = 0;

    // Indicates that we are no longer receiving distribution config from the cluster controller,
    // and that the process' own distribution config should be used. This is a safety valve in
    // the case the cluster controller is rolled back or reconfigured to not send distribution
    // config as part of state bundles.
    // This may trigger a distribution change on the next tick if internal distribution differs
    // from that previously received from the cluster controller.
    virtual void revert_distribution_source_of_truth_to_node_internal_config() = 0;
};

}
