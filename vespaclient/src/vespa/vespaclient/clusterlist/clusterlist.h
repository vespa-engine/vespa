// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config-cluster-list.h>
#include <vespa/vespalib/util/exceptions.h>

namespace vespaclient {

VESPA_DEFINE_EXCEPTION(VCClusterNotFoundException, vespalib::IllegalArgumentException);

/**
   Contains a list of all the different clusters in the
   vespa application. Currently supports only content clusters.
*/
class ClusterList
{
public:
    using ClusterNotFoundException = VCClusterNotFoundException;
    class Cluster {
    public:
        Cluster(const std::string& name, const std::string& configId);
        Cluster(const Cluster &);
        Cluster & operator = (const Cluster &);
        Cluster(Cluster &&) = default;
        Cluster & operator = (Cluster &&) = default;
        ~Cluster();

        const std::string& getName() const { return _name; }
        const std::string& getConfigId() const { return _configId; }
    private:
        std::string _name;
        std::string _configId;
    };

    ClusterList();
    ~ClusterList();

    const std::vector<Cluster>& getContentClusters() const { return _contentClusters; }


    /**
       If the given cluster exists, or if it is empty and there exists only one content cluster,
       return the cluster. Otherwise, throws a ClusterErrorException.
    */
    const Cluster& verifyContentCluster(const std::string& contentCluster) const;

private:
    void configure(const cloud::config::ClusterListConfig& cfg);
    std::string getContentClusterList() const;

    std::vector<Cluster> _contentClusters;
};

}
