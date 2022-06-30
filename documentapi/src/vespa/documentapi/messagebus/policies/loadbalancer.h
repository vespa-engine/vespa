// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/common.h>
#include <vespa/slobrok/imirrorapi.h>
#include <mutex>

namespace documentapi {

class LoadBalancer {
public:
    LoadBalancer(const string& cluster, const string& session);
    ~LoadBalancer();

    string getLastSpec(size_t target) const;
    double getWeight(size_t target) const;

    /**
       Returns the spec and the node index of the node we should send to.
       If none are found, node index is -1.
    */
    std::pair<string, int> getRecipient(const slobrok::api::IMirrorAPI::SpecList& choices);

    void received(uint32_t nodeIndex, bool busy);
private:
    using lock_guard = std::lock_guard<std::mutex>;
    std::pair<string, int> getRecipient(const lock_guard & guard, const slobrok::api::IMirrorAPI::SpecList& choices);
    void normalizeWeights(const lock_guard & guard);
    uint32_t getIndex(const string& name) const;

    class NodeInfo {
    public:
        NodeInfo() noexcept : weight(1.0), sent(0), busy(0), valid(false), lastSpec() {}

        double weight;
        uint32_t sent;
        uint32_t busy;
        bool valid;
        string lastSpec;
    };
    mutable std::mutex _mutex;
    std::vector<NodeInfo> _nodeInfo;
    string _cluster;
    string _session;
    double _position;
};

}

