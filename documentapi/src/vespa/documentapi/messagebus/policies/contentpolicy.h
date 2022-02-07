// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "externslobrokpolicy.h"
#include <vespa/documentapi/messagebus/messages/wrongdistributionreply.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/messagebus/routing/hop.h>

namespace config {
    class ICallback;
    class ConfigFetcher;
}

namespace storage {
namespace lib {
    class Distribution;
    class ClusterState;
}
}

namespace documentapi {

class ContentPolicy : public ExternSlobrokPolicy
{
private:
    document::BucketIdFactory _bucketIdFactory;
    std::unique_ptr<storage::lib::ClusterState> _state;
    string _clusterName;
    string _clusterConfigId;
    std::unique_ptr<config::ICallback>          _callBack;
    std::unique_ptr<config::ConfigFetcher>      _configFetcher;
    std::unique_ptr<storage::lib::Distribution> _distribution;
    std::unique_ptr<storage::lib::Distribution> _nextDistribution;

    mbus::Hop getRecipient(mbus::RoutingContext& context, int distributor);

public:
    ContentPolicy(const string& param);
    ~ContentPolicy();
    void doSelect(mbus::RoutingContext &context) override;
    void merge(mbus::RoutingContext &context) override;

    void updateStateFromReply(WrongDistributionReply& reply);

    /**
     * @return a pointer to the system state registered with this policy. If
     * we haven't received a system state yet, returns NULL.
     */
    const storage::lib::ClusterState* getSystemState() const { return _state.get(); }

    virtual void configure(std::unique_ptr<storage::lib::Distribution::DistributionConfig> config);
    string init() override;

private:
    string createConfigId(const string & clusterName) const;
    string createPattern(const string & clusterName, int distributor) const;
};

}
