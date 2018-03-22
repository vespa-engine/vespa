// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::Bouncer
 * @ingroup storageserver
 *
 * @brief Denies messages from entering if state is not good.
 *
 * If we are not in up state, but process is still running, only a few
 * messages should be allowed through. This link stops all messages not allowed.
 */

#pragma once

#include <vespa/config/helper/configfetcher.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storage/common/storagelink.h>
#include <vespa/storage/config/config-stor-bouncer.h>
#include <vespa/vespalib/util/sync.h>
#include <unordered_map>

namespace config { class ConfigUri; }

namespace storage {

class BouncerMetrics;

class Bouncer : public StorageLink,
                private StateListener,
                private config::IFetcherCallback<vespa::config::content::core::StorBouncerConfig>
{
    std::unique_ptr<vespa::config::content::core::StorBouncerConfig> _config;
    StorageComponent _component;
    vespalib::Lock _lock;
    lib::NodeState _baselineNodeState;
    using BucketSpaceNodeStateMapping = std::unordered_map<document::BucketSpace, lib::NodeState, document::BucketSpace::hash>;
    BucketSpaceNodeStateMapping _derivedNodeStates;
    const lib::State* _clusterState;
    config::ConfigFetcher _configFetcher;
    std::unique_ptr<BouncerMetrics> _metrics;

public:
    Bouncer(StorageComponentRegister& compReg, const config::ConfigUri & configUri);
    ~Bouncer() override;

    void print(std::ostream& out, bool verbose,
               const std::string& indent) const override;

    void configure(std::unique_ptr<vespa::config::content::core::StorBouncerConfig> config) override;

    const BouncerMetrics& metrics() const noexcept;

private:
    void validateConfig(
            const vespa::config::content::core::StorBouncerConfig&) const;

    void onClose() override;

    void abortCommandForUnavailableNode(api::StorageMessage&,
                                        const lib::State&);

    void rejectCommandWithTooHighClockSkew(api::StorageMessage& msg,
                                           int maxClockSkewInSeconds);

    void abortCommandDueToClusterDown(api::StorageMessage&);

    void rejectDueToInsufficientPriority(api::StorageMessage&,
                                         api::StorageMessage::Priority);

    bool clusterIsUp() const;

    bool isExternalLoad(const api::MessageType&) const noexcept;

    bool isExternalWriteOperation(const api::MessageType&) const noexcept;

    bool priorityRejectionIsEnabled(int configuredPriority) const noexcept {
        return (configuredPriority != -1);
    }

    /**
     * If msg is a command containing a mutating timestamp (put, remove or
     * update commands), return that timestamp. Otherwise, return 0.
     */
    uint64_t extractMutationTimestampIfAny(const api::StorageMessage& msg);

    bool onDown(const std::shared_ptr<api::StorageMessage>&) override;

    void handleNewState() override;
    const lib::NodeState &getDerivedNodeState(document::BucketSpace bucketSpace);

};

} // storage
