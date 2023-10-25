// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Component which rejects messages that can not be accepted by the node in
 * its current state.
 */

#pragma once

#include <vespa/config/helper/ifetchercallback.h>
#include <vespa/vdslib/state/nodestate.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storage/common/storagelink.h>
#include <vespa/storage/config/config-stor-bouncer.h>
#include <unordered_map>

namespace config {
    class ConfigUri;
    class ConfigFetcher;
}

namespace storage {

struct BouncerMetrics;

class Bouncer : public StorageLink,
                private StateListener
{
    using StorBouncerConfig = vespa::config::content::core::StorBouncerConfig;
    using BucketSpaceNodeStateMapping = std::unordered_map<document::BucketSpace, lib::NodeState, document::BucketSpace::hash>;

    std::unique_ptr<StorBouncerConfig> _config;
    StorageComponent                   _component;
    mutable std::mutex                 _lock;
    lib::NodeState                     _baselineNodeState;
    BucketSpaceNodeStateMapping        _derivedNodeStates;
    const lib::State*                  _clusterState;
    std::unique_ptr<BouncerMetrics>    _metrics;
    bool                               _closed;

public:
    Bouncer(StorageComponentRegister& compReg, const StorBouncerConfig& bootstrap_config);
    ~Bouncer() override;

    void print(std::ostream& out, bool verbose,
               const std::string& indent) const override;

    void on_configure(const StorBouncerConfig& config);
    const BouncerMetrics& metrics() const noexcept;

private:
    void validateConfig(const vespa::config::content::core::StorBouncerConfig&) const;
    void onClose() override;
    void abortCommandForUnavailableNode(api::StorageMessage&, const lib::State&);
    void rejectCommandWithTooHighClockSkew(api::StorageMessage& msg, int maxClockSkewInSeconds);
    void abortCommandDueToClusterDown(api::StorageMessage&, const lib::State&);
    void rejectDueToInsufficientPriority(api::StorageMessage&, api::StorageMessage::Priority);
    void reject_due_to_too_few_bucket_bits(api::StorageMessage&);
    void reject_due_to_node_shutdown(api::StorageMessage&);
    static bool clusterIsUp(const lib::State& cluster_state);
    bool isDistributor() const;
    static bool isExternalLoad(const api::MessageType&) noexcept;
    static bool isExternalWriteOperation(const api::MessageType&) noexcept;
    static bool priorityRejectionIsEnabled(int configuredPriority) noexcept {
        return (configuredPriority != -1);
    }

    /**
     * If msg is a command containing a mutating timestamp (put, remove or
     * update commands), return that timestamp. Otherwise, return 0.
     */
    static uint64_t extractMutationTimestampIfAny(const api::StorageMessage& msg);
    bool onDown(const std::shared_ptr<api::StorageMessage>&) override;
    void handleNewState() noexcept override;
    const lib::NodeState &getDerivedNodeState(document::BucketSpace bucketSpace) const;
    void append_node_identity(std::ostream& target_stream) const;
};

} // storage
