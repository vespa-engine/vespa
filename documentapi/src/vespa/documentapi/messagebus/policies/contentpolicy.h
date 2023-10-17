// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "externslobrokpolicy.h"
#include <vespa/documentapi/messagebus/messages/wrongdistributionreply.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/messagebus/routing/hop.h>
#include <shared_mutex>

namespace config {
    class ICallback;
    class ConfigFetcher;
}

namespace storage::lib {
    class Distribution;
    class ClusterState;
}

namespace documentapi {

class ContentPolicy : public ExternSlobrokPolicy
{
private:
    document::BucketIdFactory                         _bucketIdFactory;
    mutable std::shared_mutex                         _rw_lock;
    std::shared_ptr<const storage::lib::ClusterState> _state;
    string                                            _clusterName;
    string                                            _clusterConfigId;
    std::unique_ptr<config::ICallback>                _callBack;
    std::unique_ptr<config::ConfigFetcher>            _configFetcher;
    std::shared_ptr<const storage::lib::Distribution> _distribution;

    using StateSnapshot = std::pair<std::shared_ptr<const storage::lib::ClusterState>,
                                    std::shared_ptr<const storage::lib::Distribution>>;

    // Acquires _lock
    [[nodiscard]] StateSnapshot internal_state_snapshot();

    mbus::Hop getRecipient(mbus::RoutingContext& context, int distributor);
    // Acquires _lock
    void updateStateFromReply(WrongDistributionReply& reply);
    // Acquires _lock
    void reset_state();

public:
    explicit ContentPolicy(const string& param);
    ~ContentPolicy() override;
    void doSelect(mbus::RoutingContext &context) override;
    void merge(mbus::RoutingContext &context) override;

    /**
     * @return a pointer to the system state registered with this policy. If
     * we haven't received a system state yet, returns nullptr.
     */
    std::shared_ptr<const storage::lib::ClusterState> getSystemState() const noexcept;

    virtual void configure(std::unique_ptr<storage::lib::Distribution::DistributionConfig> config);
    string init() override;

private:
    static string createConfigId(const string & clusterName);
    static string createPattern(const string & clusterName, int distributor);
};

}
