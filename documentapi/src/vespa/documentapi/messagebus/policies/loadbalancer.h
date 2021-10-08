// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/common.h>
#include <vespa/slobrok/imirrorapi.h>

namespace documentapi {

class LoadBalancer {
public:
    class NodeInfo {
    public:
        NodeInfo() : valid(false), sent(0), busy(0), weight(1.0) {};

        bool valid;
        uint32_t sent;
        uint32_t busy;
        double weight;
        string lastSpec;
    };

    std::vector<NodeInfo> _nodeInfo;
    string _cluster;
    string _session;
    double _position;

    LoadBalancer(const string& cluster, const string& session);
    ~LoadBalancer();

    const std::vector<NodeInfo>& getNodeInfo() const { return _nodeInfo; }

    uint32_t getIndex(const string& name) const;

    /**
       Returns the spec and the node index of the node we should send to.
       If none are found, node index is -1.
    */
    std::pair<string, int> getRecipient(const slobrok::api::IMirrorAPI::SpecList& choices);

    void normalizeWeights();

    void received(uint32_t nodeIndex, bool busy);
};

}

