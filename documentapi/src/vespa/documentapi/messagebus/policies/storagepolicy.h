// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/documentapi/messagebus/messages/wrongdistributionreply.h>
#include <vespa/messagebus/reply.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/documentapi/messagebus/policies/externslobrokpolicy.h>

namespace documentapi {

class StoragePolicy : public boost::noncopyable,
                      public ExternSlobrokPolicy,
                      public config::IFetcherCallback<vespa::config::content::StorDistributionConfig>
{
private:
    document::BucketIdFactory _bucketIdFactory;
    std::unique_ptr<storage::lib::ClusterState> _state;
    string _clusterName;
    string _clusterConfigId;
    std::unique_ptr<config::ConfigFetcher> _configFetcher;
    std::unique_ptr<storage::lib::Distribution> _distribution;
    std::unique_ptr<storage::lib::Distribution> _nextDistribution;

    mbus::Hop getRecipient(mbus::RoutingContext& context, int distributor);

public:
    StoragePolicy(const string& param);
    virtual ~StoragePolicy();

    // Inherit doc from IRoutingPolicy.
    virtual void doSelect(mbus::RoutingContext &context);

    // Inherit doc from IRoutingPolicy.
    virtual void merge(mbus::RoutingContext &context);

    void updateStateFromReply(WrongDistributionReply& reply);

    /**
     * @return a pointer to the system state registered with this policy. If
     * we haven't received a system state yet, returns NULL.
     */
    const storage::lib::ClusterState* getSystemState() const
        { return _state.get(); }

    void configure(std::unique_ptr<vespa::config::content::StorDistributionConfig> config);

    string init();

private:
    virtual string createConfigId(const string & clusterName) const;
    string createPattern(const string & clusterName, int distributor) const;
};

}

