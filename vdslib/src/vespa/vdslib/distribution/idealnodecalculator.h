// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * An interface to implement for a calculator calcuting ideal state. It should
 * be easy to wrap this calculator in a cache. Thus options that seldom change,
 * are taken in as set parameters, such that existing cache can be invalidated.
 */
#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/util/printable.h>
#include <vespa/vdslib/state/node.h>
#include <vector>
#include <memory>

namespace storage::lib {

class Distribution;
class ClusterState;

/**
 * A list of ideal nodes, sorted in preferred order. Wraps a vector to hide
 * unneeded details, and make it easily printable.
 */
class IdealNodeList : public document::Printable {
    std::vector<Node> _idealNodes;

public:
    IdealNodeList();
    ~IdealNodeList();

    void push_back(const Node& node) {
        _idealNodes.push_back(node);
    }

    const Node& operator[](uint32_t i) const { return _idealNodes[i]; }
    uint32_t size() const { return _idealNodes.size(); }
    bool contains(const Node& n) const {
        for (uint32_t i=0; i<_idealNodes.size(); ++i) {
            if (n == _idealNodes[i]) return true;
        }
        return false;
    }
    uint16_t indexOf(const Node& n) const {
        for (uint16_t i=0; i<_idealNodes.size(); ++i) {
            if (n == _idealNodes[i]) return i;
        }
        return 0xffff;
    }

    void print(std::ostream& out, bool, const std::string &) const override;
};

/**
 * Simple interface to use for those who needs to calculate ideal nodes.
 */
class IdealNodeCalculator {
public:
    typedef std::shared_ptr<IdealNodeCalculator> SP;
    enum UpStates {
        UpInit,
        UpInitMaintenance,
        UP_STATE_COUNT
    };

    virtual ~IdealNodeCalculator() = default;

    virtual IdealNodeList getIdealNodes(const NodeType&,
                                        const document::BucketId&,
                                        UpStates upStates = UpInit) const = 0;

    // Wrapper functions to make prettier call if nodetype is given.
    IdealNodeList getIdealDistributorNodes(const document::BucketId& bucket,
                                           UpStates upStates = UpInit) const
        { return getIdealNodes(NodeType::DISTRIBUTOR, bucket, upStates); }
    IdealNodeList getIdealStorageNodes(const document::BucketId& bucket,
                                       UpStates upStates = UpInit) const
        { return getIdealNodes(NodeType::STORAGE, bucket, upStates); }
};


/**
 * More complex interface that provides a way to alter needed settings not
 * provided in the function call itself.
 */
class IdealNodeCalculatorConfigurable : public IdealNodeCalculator
{
public:
    typedef std::shared_ptr<IdealNodeCalculatorConfigurable> SP;

    virtual void setDistribution(const Distribution&) = 0;
    virtual void setClusterState(const ClusterState&) = 0;
};

}
